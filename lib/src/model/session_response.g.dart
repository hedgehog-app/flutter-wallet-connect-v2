// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionResponse _$SessionResponseFromJson(Map<String, dynamic> json) =>
    SessionResponse(
      id: json['id'] as String,
      topic: json['topic'] as String,
      chainId: json['chainId'] as String?,
      result: json['result'],
    );

Map<String, dynamic> _$SessionResponseToJson(SessionResponse instance) =>
    <String, dynamic>{
      'id': instance.id,
      'topic': instance.topic,
      'chainId': instance.chainId,
      'result': instance.result,
    };
