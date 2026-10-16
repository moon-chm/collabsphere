package com.collabsphere.app.model.media

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.size.Dimension

class CloudinarySizingInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed(request)
        val width = (chain.size.width as? Dimension.Pixels)?.px
        val sized = CloudinaryImageUrls.sized(url, width) ?: return chain.proceed(request)
        val result = chain.proceed(request.newBuilder().data(sized).build())
        return if (result is ErrorResult) chain.proceed(request) else result
    }
}
