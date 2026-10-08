package com.gridcc.doomscore.android.network

import kotlinx.coroutines.flow.MutableStateFlow

interface LeagueGateway {
    val state:MutableStateFlow<LeagueState>
    val configured:Boolean
    val accountId:String? get()=null
    suspend fun bootstrap()
    suspend fun save(username:String,instagram:String,emoji:String,visible:Boolean,captcha:String?=null)
    suspend fun refresh()
    suspend fun loadMore() {}
    fun syncAsync()
    suspend fun profileAction(username:String,action:String,reason:String="spam")
    suspend fun delete()
}
