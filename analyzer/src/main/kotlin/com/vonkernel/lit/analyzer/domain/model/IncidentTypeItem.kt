package com.vonkernel.lit.analyzer.domain.model

data class IncidentTypeItem(
    val code: String,
    val name: String
) {
    override fun toString(): String = "- $code: $name"

    companion object {
        fun formatList(items: List<IncidentTypeItem>): String =
            items.joinToString("\n") { it.toString() }
    }
}