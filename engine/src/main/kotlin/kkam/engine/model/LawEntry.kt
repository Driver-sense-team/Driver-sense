package kkam.engine.model

import kotlinx.serialization.Serializable

/** 법규 룰 테이블 한 행. 필드 설명은 docs/law_table.md. */
@Serializable
data class LawEntry(
    val id: String,
    val topic: String,
    val situation: String,
    val article: String,
    val summary: String,
    val penalty: String,
    val sourceUrl: String,
    /** 해석이 갈리면 true. Q&A에서 단정하지 않는다. */
    val disputed: Boolean,
    /** 확인할 내용이나 주의사항. 없으면 빈 문자열. */
    val note: String,
)
