package com.project2.sudoku.model // 패키지 경로는 실제에 맞게 유지

data class SudokuCell(
    val row: Int,
    val col: Int,
    val value: Int,
    val isFixed: Boolean = false,
    val isError: Boolean = false,
    val notes: Set<Int> = emptySet() // 이 줄을 반드시 추가해야 합니다!
)