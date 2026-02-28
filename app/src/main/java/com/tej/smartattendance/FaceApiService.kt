package com.tej.smartattendance

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface FaceApiService {

    @FormUrlEncoded
    @POST("facepp/v3/compare")
    fun compareFaces(
        @Field("api_key") apiKey: String,
        @Field("api_secret") apiSecret: String,
        @Field("image_url1") imageUrl1: String,
        @Field("image_url2") imageUrl2: String
    ): Call<FaceCompareResponse>
}
