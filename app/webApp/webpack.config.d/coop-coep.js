/*
 * 跨源隔离（cross-origin isolation）头。
 *
 * Room 3.0 的 wasmJs 通过 androidx.sqlite:sqlite-web 的 WebWorkerSQLiteDriver 持久化到 OPFS，
 * 其 SyncAccessHandle 依赖 SharedArrayBuffer，浏览器要求页面处于 cross-origin isolated 状态
 * （crossOriginIsolated === true）。devServer 需返回以下两个响应头。
 *
 * 注意：生产环境托管（Nginx/CDN 等）也必须对页面返回同样的两个头，否则线上 OPFS 同样失败。
 * 引入 COEP: require-corp 后，所有跨域子资源需带 CORP/CORS 头或改为同源。
 */
if (config.devServer) {
    config.devServer.headers = Object.assign({}, config.devServer.headers, {
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
    })
}
