package com.storyteller_f.a.backend.core

class UnauthorizedException : Exception()

class ForbiddenException(message: String = "Invalid operation") : Exception(message)

class CustomBadRequestException(message: String) : Exception(message)
