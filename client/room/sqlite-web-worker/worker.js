/*
 * Web Worker implementing the androidx.sqlite `WebWorkerSQLiteDriver` message protocol
 * on top of @sqlite.org/sqlite-wasm, persisting the database in OPFS.
 *
 * Protocol (see androidx.sqlite.driver.web.DatabaseWebWorkerImpl):
 *   driver -> worker:  { id, data: { cmd, ... } }
 *   worker -> driver:  { id, data: <result>, error: <string|null> }
 *
 * Commands:
 *   open    { fileName }                        -> { databaseId }
 *   prepare { databaseId, sql }                 -> { statementId, parameterCount, columnNames }
 *   step    { statementId, bindings }           -> { rows, columnTypes }
 *   close   { statementId?, databaseId? }       -> (no response on success; one-way request)
 *
 * Notes:
 *   - `bindings` is 0-based (bindings[0] is the first SQL parameter); SQLite bind is 1-based.
 *   - `step` executes the whole statement and returns ALL rows at once; the driver iterates
 *     the cached rows locally. `columnTypes` are derived from the first row.
 *   - OPFS requires a Worker context + cross-origin isolation (COOP/COEP). When unavailable
 *     the worker falls back to a transient in-memory database and warns.
 */

import sqlite3InitModule from '@sqlite.org/sqlite-wasm'

// SQLite fundamental datatypes (matches androidx.sqlite SQLITE_DATA_* / SQLite C API).
const SQLITE_INTEGER = 1
const SQLITE_FLOAT = 2
const SQLITE_TEXT = 3
const SQLITE_BLOB = 4
const SQLITE_NULL = 5

let sqlite3 = null
const databases = new Map() // databaseId -> oo1.DB
const statements = new Map() // statementId -> { databaseId, sql, statement }
let nextDatabaseId = 1
let nextStatementId = 1

async function ensureSqlite3() {
  if (sqlite3 === null) {
    sqlite3 = await sqlite3InitModule()
  }
  return sqlite3
}

function openDatabase(fileName) {
  if (sqlite3.oo1.OpfsDb) {
    return new sqlite3.oo1.OpfsDb(fileName, 'c')
  }
  console.warn(
    '[sqlite-web-worker] OPFS VFS unavailable (page must be a Worker context and ' +
      'cross-origin isolated via COOP/COEP). Falling back to a transient, non-persistent database.'
  )
  return new sqlite3.oo1.DB(fileName, 'c')
}

function handleOpen(data) {
  const db = openDatabase(data.fileName)
  const databaseId = nextDatabaseId++
  databases.set(databaseId, db)
  return { databaseId }
}

function handlePrepare(data) {
  const db = databases.get(data.databaseId)
  if (!db) throw new Error('Unknown databaseId: ' + data.databaseId)
  const stmt = db.prepare(data.sql)
  const statementId = nextStatementId++
  statements.set(statementId, {
    databaseId: data.databaseId,
    sql: data.sql,
    statement: stmt,
  })
  const columnNames = []
  const columnCount = stmt.columnCount
  for (let i = 0; i < columnCount; i++) {
    columnNames.push(stmt.getColumnName(i))
  }
  return {
    statementId,
    parameterCount: stmt.parameterCount,
    columnNames,
  }
}

function handleStep(data) {
  const statementRecord = statements.get(data.statementId)
  if (!statementRecord) throw new Error('Unknown statementId: ' + data.statementId)
  const db = databases.get(statementRecord.databaseId)
  if (!db) throw new Error('Unknown databaseId: ' + statementRecord.databaseId)
  const stmt = statementRecord.statement

  const transactionOperation = getTransactionOperation(statementRecord.sql)
  const isAutoCommit = sqlite3.capi.sqlite3_get_autocommit(db.pointer) !== 0
  if (transactionOperation === 'begin' && !isAutoCommit) {
    // A suspended driver request can be cancelled after SQLite executes BEGIN but
    // before the WebWorkerSQLiteConnection receives the response and records its
    // transaction state. Room then cannot see or roll back that orphaned transaction.
    db.exec('ROLLBACK TRANSACTION')
  } else if (
    (transactionOperation === 'end' || transactionOperation === 'rollback') &&
    isAutoCommit
  ) {
    // The inverse race can cancel an END/ROLLBACK response after SQLite has
    // completed it. Treat a repeated finalizer as successful so the driver can
    // synchronize its transaction state with SQLite.
    return { rows: [], columnTypes: [] }
  }

  // The statement may be re-executed with fresh bindings, so reset first.
  stmt.reset(true)

  const bindings = data.bindings || []
  const parameterCount = stmt.parameterCount
  for (let i = 0; i < parameterCount; i++) {
    const value = i < bindings.length ? bindings[i] : null
    stmt.bind(i + 1, value === undefined ? null : value)
  }

  const columnCount = stmt.columnCount
  const rows = []
  let columnTypes = null
  while (stmt.step()) {
    const row = new Array(columnCount)
    for (let i = 0; i < columnCount; i++) {
      row[i] = stmt.get(i)
    }
    rows.push(row)
    if (columnTypes === null) {
      columnTypes = new Array(columnCount)
      for (let i = 0; i < columnCount; i++) {
        columnTypes[i] = sqlite3.capi.sqlite3_column_type(stmt.pointer, i)
      }
    }
  }
  if (columnTypes === null) {
    columnTypes = new Array(columnCount).fill(SQLITE_NULL)
  }
  return { rows, columnTypes }
}

function getTransactionOperation(sql) {
  const prefix = sql.trimStart().slice(0, 3).toUpperCase()
  if (prefix === 'BEG') return 'begin'
  if (prefix === 'END' || prefix === 'COM') return 'end'
  if (prefix === 'ROL' && !/\sTO\s/i.test(sql)) return 'rollback'
  return null
}

function handleClose(data) {
  if (data.statementId != null) {
    const statementRecord = statements.get(data.statementId)
    if (statementRecord) {
      statementRecord.statement.finalize()
      statements.delete(data.statementId)
    }
  }
  if (data.databaseId != null) {
    const db = databases.get(data.databaseId)
    if (db) {
      db.close()
      databases.delete(data.databaseId)
    }
  }
}

function handleCommand(data) {
  switch (data.cmd) {
    case 'open':
      return handleOpen(data)
    case 'prepare':
      return handlePrepare(data)
    case 'step':
      return handleStep(data)
    default:
      throw new Error('Unknown command: ' + data.cmd)
  }
}

self.onmessage = async (event) => {
  const message = event.data
  const id = message.id
  const data = message.data
  // `close` is a one-way request: the driver does not await it and treats any
  // unexpected (non-error) response as a protocol violation, so never reply on success.
  if (data && data.cmd === 'close') {
    try {
      await ensureSqlite3()
      handleClose(data)
    } catch (err) {
      console.error('[sqlite-web-worker] close failed:', err)
    }
    return
  }
  try {
    await ensureSqlite3()
    const result = handleCommand(data)
    self.postMessage({ id, data: result, error: null })
  } catch (err) {
    const error = err && err.message ? err.message : String(err)
    self.postMessage({ id, data: null, error })
  }
}
