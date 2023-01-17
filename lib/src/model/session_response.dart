import 'dart:convert';

import 'package:json_annotation/json_annotation.dart';

part 'session_response.g.dart';

@JsonSerializable(explicitToJson: true)
class SessionResponse {
  final String id;
  final String topic;
  final String chainId;
  final String result;

  SessionResponse(
      {required this.id,
      required this.topic,
      required this.chainId,
      required this.result});

  factory SessionResponse.fromJson(Map<String, dynamic> json) =>
      _$SessionResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SessionResponseToJson(this);

  static List<dynamic> fromParamsJson(String json) {
    return jsonDecode(json) as List;
  }
}