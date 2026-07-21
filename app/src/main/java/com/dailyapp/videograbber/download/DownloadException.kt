package com.dailyapp.videograbber.download

/**
 * Carries a short, user-facing reason (Korean) so the UI can show
 * *why* a download could not be found/completed, not just that it failed.
 */
class DownloadException(userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {

    companion object {
        fun invalidUrl() = DownloadException("올바른 URL이 아닙니다.")

        fun notFound(url: String) = DownloadException("영상을 찾을 수 없습니다 (404). 링크가 만료되었거나 잘못된 주소일 수 있어요.")

        fun httpError(code: Int) = DownloadException(
            when (code) {
                401, 403 -> "접근이 거부되었습니다 (HTTP $code). 로그인이 필요하거나 접근이 제한된 영상일 수 있어요."
                404 -> "영상을 찾을 수 없습니다 (404)."
                in 500..599 -> "서버 오류입니다 (HTTP $code). 잠시 후 다시 시도해 주세요."
                else -> "서버가 요청을 거부했습니다 (HTTP $code)."
            }
        )

        fun networkError(cause: Throwable) = DownloadException(
            "네트워크 오류로 다운로드하지 못했습니다. 인터넷 연결을 확인해 주세요.", cause
        )

        fun notAVideo() = DownloadException(
            "이 주소에서 영상을 찾지 못했습니다. 페이지 링크가 아니라 실제 영상(mp4/m3u8) 주소인지 확인해 주세요."
        )

        fun emptyPlaylist() = DownloadException(
            "재생목록(m3u8)에 영상 조각이 없습니다. 스트림이 종료되었거나 잘못된 링크일 수 있어요."
        )

        fun unsupportedProtection() = DownloadException(
            "이 영상은 DRM(SAMPLE-AES 등)으로 보호되어 있어 다운로드를 지원하지 않습니다."
        )

        fun keyFetchFailed() = DownloadException(
            "암호화 키를 가져오지 못해 복호화할 수 없습니다."
        )

        fun storageError(cause: Throwable) = DownloadException(
            "기기에 파일을 저장하는 중 오류가 발생했습니다. 저장 공간을 확인해 주세요.", cause
        )

        fun unknown(cause: Throwable) = DownloadException(
            cause.message ?: "알 수 없는 오류로 다운로드에 실패했습니다.", cause
        )
    }
}
