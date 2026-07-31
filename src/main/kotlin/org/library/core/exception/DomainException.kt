package org.library.core.exception

class DomainException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
