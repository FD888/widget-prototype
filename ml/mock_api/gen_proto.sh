#!/usr/bin/env bash
# Одноразовый скрипт для регенерации gRPC-стабов Яндекс SpeechKit v2.
# Запускать из директории ml/mock_api/ с активным venv.
#
# Использует минимальный proto (только StreamingRecognize, без внешних зависимостей).
# После генерации стабы коммитятся в репо — в Docker-образе компиляция не нужна.
#
# Использование:
#   source venv/bin/activate
#   pip install grpcio-tools
#   bash gen_proto.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROTO_FILE="$SCRIPT_DIR/stt_minimal.proto"

# Создаём минимальный proto только с нужным для StreamingRecognize
cat > "$PROTO_FILE" << 'PROTO'
syntax = "proto3";
package yandex.cloud.ai.stt.v2;

service SttService {
  rpc StreamingRecognize (stream StreamingRecognitionRequest) returns (stream StreamingRecognitionResponse) {}
}

message StreamingRecognitionRequest {
  oneof streaming_request {
    RecognitionConfig config = 1;
    bytes audio_content = 2;
  }
}

message StreamingRecognitionResponse {
  repeated SpeechRecognitionChunk chunks = 1;
}

message RecognitionConfig {
  RecognitionSpec specification = 1;
  string folder_id = 2;
}

message RecognitionSpec {
  enum AudioEncoding {
    AUDIO_ENCODING_UNSPECIFIED = 0;
    LINEAR16_PCM = 1;
    OGG_OPUS = 2;
    MP3 = 3;
  }
  AudioEncoding audio_encoding = 1;
  int64 sample_rate_hertz = 2;
  string language_code = 3;
  bool profanity_filter = 4;
  string model = 5;
  bool partial_results = 7;
  bool single_utterance = 8;
  int64 audio_channel_count = 9;
  bool raw_results = 10;
  bool literature_text = 11;
}

message SpeechRecognitionChunk {
  repeated SpeechRecognitionAlternative alternatives = 1;
  bool final = 2;
  bool end_of_utterance = 3;
}

message SpeechRecognitionAlternative {
  string text = 1;
  float confidence = 2;
}
PROTO

mkdir -p "$SCRIPT_DIR/yandex_speech"
touch "$SCRIPT_DIR/yandex_speech/__init__.py"

python -m grpc_tools.protoc \
    --proto_path="$SCRIPT_DIR" \
    --python_out="$SCRIPT_DIR/yandex_speech" \
    --grpc_python_out="$SCRIPT_DIR/yandex_speech" \
    "$PROTO_FILE"

# Переименовываем и исправляем импорт
mv "$SCRIPT_DIR/yandex_speech/stt_minimal_pb2.py" "$SCRIPT_DIR/yandex_speech/stt_pb2.py"
mv "$SCRIPT_DIR/yandex_speech/stt_minimal_pb2_grpc.py" "$SCRIPT_DIR/yandex_speech/stt_pb2_grpc.py"

python -c "
content = open('$SCRIPT_DIR/yandex_speech/stt_pb2_grpc.py').read()
content = content.replace(
    'import stt_minimal_pb2 as stt__minimal__pb2',
    'from yandex_speech import stt_pb2 as stt__minimal__pb2'
)
open('$SCRIPT_DIR/yandex_speech/stt_pb2_grpc.py', 'w').write(content)
print('Import patched OK')
"

rm "$PROTO_FILE"
echo "Done. Stubs written to yandex_speech/"
