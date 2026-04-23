# Graph Report - /Users/xyz/AndroidStudioProjects/edugate  (2026-04-22)

## Corpus Check
- 29 files · ~12,817 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 166 nodes · 137 edges · 29 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]

## God Nodes (most connected - your core abstractions)
1. `SmartBoardViewModel` - 23 edges
2. `ClassroomViewModel` - 7 edges
3. `StudentViewModel` - 6 edges
4. `AuthViewModel` - 5 edges
5. `TeacherViewModel` - 5 edges
6. `TimetableViewModel` - 4 edges
7. `JoinRequestsViewModel` - 3 edges
8. `ExampleInstrumentedTest` - 2 edges
9. `ExampleUnitTest` - 2 edges
10. `MainActivity` - 2 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (2): Handle, SmartBoardViewModel

### Community 1 - "Community 1"
Cohesion: 0.12
Nodes (2): ClassroomSection, HomeOption

### Community 2 - "Community 2"
Cohesion: 0.17
Nodes (6): Error, Idle, JoinStatus, Loading, StudentViewModel, Success

### Community 3 - "Community 3"
Cohesion: 0.17
Nodes (9): ClassroomDetails, JoinRequests, Login, Register, Screen, SmartBoard, Splash, StudentDashboard (+1 more)

### Community 4 - "Community 4"
Cohesion: 0.18
Nodes (6): AuthState, AuthViewModel, Error, Idle, Loading, Success

### Community 5 - "Community 5"
Cohesion: 0.22
Nodes (1): MainActivity

### Community 6 - "Community 6"
Cohesion: 0.25
Nodes (7): BoardElement, EmojiElement, PathElement, ShapeElement, ShapeType, TextElement, Tool

### Community 7 - "Community 7"
Cohesion: 0.25
Nodes (1): ClassroomViewModel

### Community 8 - "Community 8"
Cohesion: 0.29
Nodes (0): 

### Community 9 - "Community 9"
Cohesion: 0.33
Nodes (1): TeacherViewModel

### Community 10 - "Community 10"
Cohesion: 0.4
Nodes (0): 

### Community 11 - "Community 11"
Cohesion: 0.4
Nodes (1): TimetableViewModel

### Community 12 - "Community 12"
Cohesion: 0.5
Nodes (2): EdugateApp, EdugateFileProvider

### Community 13 - "Community 13"
Cohesion: 0.5
Nodes (0): 

### Community 14 - "Community 14"
Cohesion: 0.5
Nodes (1): JoinRequestsViewModel

### Community 15 - "Community 15"
Cohesion: 0.67
Nodes (1): ExampleInstrumentedTest

### Community 16 - "Community 16"
Cohesion: 0.67
Nodes (1): ExampleUnitTest

### Community 17 - "Community 17"
Cohesion: 0.67
Nodes (1): ThemeViewModel

### Community 18 - "Community 18"
Cohesion: 0.67
Nodes (2): DaySchedule, Timetable

### Community 19 - "Community 19"
Cohesion: 0.67
Nodes (1): EduFile

### Community 20 - "Community 20"
Cohesion: 0.67
Nodes (1): Classroom

### Community 21 - "Community 21"
Cohesion: 0.67
Nodes (0): 

### Community 22 - "Community 22"
Cohesion: 1.0
Nodes (0): 

### Community 23 - "Community 23"
Cohesion: 1.0
Nodes (1): JoinRequest

### Community 24 - "Community 24"
Cohesion: 1.0
Nodes (0): 

### Community 25 - "Community 25"
Cohesion: 1.0
Nodes (0): 

### Community 26 - "Community 26"
Cohesion: 1.0
Nodes (0): 

### Community 27 - "Community 27"
Cohesion: 1.0
Nodes (0): 

### Community 28 - "Community 28"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **31 isolated node(s):** `EdugateFileProvider`, `AuthState`, `Idle`, `Loading`, `Success` (+26 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 22`** (2 nodes): `EdugateTheme()`, `Theme.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 23`** (2 nodes): `JoinRequest`, `JoinRequest.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 24`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 25`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 26`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 27`** (1 nodes): `Color.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 28`** (1 nodes): `Type.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `EdugateFileProvider`, `AuthState`, `Idle` to the rest of the system?**
  _31 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._