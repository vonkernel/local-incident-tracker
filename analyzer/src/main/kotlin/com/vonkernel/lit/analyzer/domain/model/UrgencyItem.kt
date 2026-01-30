package com.vonkernel.lit.analyzer.domain.model

data class UrgencyItem(
    val name: String,
    val level: Int
) {
    override fun toString(): String = "- $name (level: $level)"

    companion object {
        fun formatList(items: List<UrgencyItem>): String =
            items.joinToString("\n") { it.toString() }
    }
}