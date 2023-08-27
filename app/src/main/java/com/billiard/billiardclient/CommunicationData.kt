package com.billiard.billiardclient

data class CommunicationData(
    val funcName: String,
    var sendTime: Long = 0,
    var receiveTime: Long = 0,
    var data: String = ""
)
