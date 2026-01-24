package com.project2.sudoku // 본인의 패키지 이름으로 되어 있는지 확인하세요!

/**
 * 스도쿠의 각 칸을 정의하는 데이터 클래스
 */
data class SudokuCell(
    val row: Int,           // 행 (0~8)
    val col: Int,           // 열 (0~8)
    var value: Int = 0,     // 입력된 숫자 (0은 빈칸)
    val isFixed: Boolean = false, // 문제로 주어진 고정 숫자인지 여부
    var isError: Boolean = false, // 잘못 입력된 숫자인지 여부 (중복 체크용)
    var isSelected: Boolean = false // 현재 사용자가 클릭하여 선택했는지 여부
)