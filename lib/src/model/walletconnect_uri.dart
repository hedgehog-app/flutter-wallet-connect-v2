import 'dart:convert';

import 'package:json_annotation/json_annotation.dart';

part 'walletconnect_uri.g.dart';

@JsonSerializable(explicitToJson: true)
class WalletConnectUri {
  final String fullUri;
  final String topic;

  WalletConnectUri({required this.fullUri, required this.topic});

  factory WalletConnectUri.fromJson(Map<String, dynamic> json) =>
      _$WalletConnectUriFromJson(json);

  Map<String, dynamic> toJson() => _$WalletConnectUriToJson(this);
}
