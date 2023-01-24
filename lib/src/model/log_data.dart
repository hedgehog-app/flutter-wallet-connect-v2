import 'package:json_annotation/json_annotation.dart';

part 'log_data.g.dart';

@JsonSerializable(explicitToJson: true)
class LogData {
  final String message;

  LogData(this.message);

  factory LogData.fromJson(Map<String, dynamic> json) =>
      _$LogDataFromJson(json);

  Map<String, dynamic> toJson() => _$LogDataToJson(this);
}
