# Gem Quest - 보석 퍼즐 게임

플레이스토어 출시 수준의 매치-3 퍼즐 게임 (Kotlin 네이티브)

## 📱 주요 기능

### 게임플레이
- 8x8 그리드의 매치-3 퍼즐
- 6가지 보석 타입 (루비, 사파이어, 에메랄드, 토파즈, 자수정, 다이아몬드)
- 부드러운 애니메이션 및 터치 반응
- 직관적인 UI/UX

### 완성도 높은 기능들
- ✅ 레벨 시스템 (난이도 점진적 상승)
- ✅ 콤보 시스템 (연속 매치로 보너스 점수)
- ✅ 목표 점수 및 이동 횟수 제한
- ✅ 튜토리얼 화면
- ✅ 리더보드 (SharedPreferences 저장)
- ✅ 설정 메뉴 (효과음, 진동, 파티클 ON/OFF)
- ✅ 레벨 완료 및 게임오버 다이얼로그
- ✅ 진동 피드백
- ✅ Material Design 3

## 🏗️ 프로젝트 구조

```
app/src/main/
├── java/com/gemquest/puzzle/
│   ├── MainActivity.kt              # 메인 메뉴
│   ├── GameActivity.kt              # 게임 화면
│   ├── TutorialActivity.kt          # 튜토리얼
│   ├── LeaderboardActivity.kt       # 리더보드
│   ├── SettingsActivity.kt          # 설정
│   ├── adapter/
│   │   └── GemAdapter.kt            # RecyclerView 어댑터
│   ├── model/
│   │   ├── Gem.kt                   # 보석 데이터 모델
│   │   └── GameState.kt             # 게임 상태 관리
│   ├── engine/
│   │   └── GameEngine.kt            # 게임 로직 엔진
│   └── viewmodel/
│       └── GameViewModel.kt         # MVVM 패턴
└── res/
    ├── layout/                      # XML 레이아웃
    ├── values/                      # 색상, 문자열, 테마
    └── drawable/                    # 그래픽 리소스
```

## 🎮 게임 로직

### GameEngine
- 그리드 초기화 및 보석 생성
- 매치 감지 알고리즘 (가로/세로)
- 보석 교환 및 유효성 검증
- 보석 낙하 및 빈 공간 채우기
- 가능한 이동 검사

### GameViewModel
- LiveData를 통한 상태 관리
- 코루틴 기반 비동기 처리
- 콤보 시스템 구현
- 점수 계산 및 레벨 진행

## 🛠️ 기술 스택

- **Language**: Kotlin 1.9.22
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM Pattern
- **UI**: Material Design 3, ViewBinding
- **Async**: Coroutines
- **Storage**: SharedPreferences

## 📦 주요 의존성

```gradle
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

## 🚀 빌드 및 실행

### Android Studio에서 실행
1. Android Studio 열기
2. "Open an Existing Project" 선택
3. GemQuest 폴더 선택
4. Gradle 동기화 대기
5. 에뮬레이터 또는 실제 기기에서 실행

### 명령줄에서 빌드
```bash
cd GemQuest
./gradlew assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

## 📝 게임 규칙

1. **기본 플레이**: 인접한 두 보석을 탭하여 교환
2. **매치**: 같은 색깔 3개 이상 연결 시 제거
3. **목표**: 제한된 이동 횟수 안에 목표 점수 달성
4. **콤보**: 연속 매치 시 점수 배율 증가
5. **레벨**: 목표 달성 시 다음 레벨로 진행

## 🎨 디자인 특징

- 보석 테마의 화려한 그라디언트 배경
- Material Design 3 컴포넌트
- 부드러운 애니메이션 효과
- 직관적인 색상 구분
- 반응형 레이아웃

## 🔧 추가 개선 가능 사항

- [ ] 사운드 파일 추가 (현재는 프로그래매틱 사운드)
- [ ] 더 많은 특수 보석 타입
- [ ] 아이템 상점 시스템
- [ ] Google Play Games 연동
- [ ] 광고 통합 (AdMob)
- [ ] 인앱 결제
- [ ] 클라우드 저장
- [ ] 다국어 지원

## 📄 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.

## 👨‍💻 개발자

Made with ❤️ by Claude
