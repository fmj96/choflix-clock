# choflix-clock 빌드 환경. 새 셸에서 한 번:  source env.sh
# JDK 17 과 안드로이드 SDK 위치. 자기 환경에 맞게 고쳐 쓴다.
export JAVA_HOME="${JAVA_HOME:-$HOME/android-sdk/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
