package com.vonkernel.lit.entity

enum class RegionType(val code: String) {
    BJDONG("B"),           // 법정동 (Judicial-dong)
    HADONG("H"),           // 행정동 (Administrative-dong)
    UNKNOWN("U");          // 구별되지 않음 (Unknown)
}