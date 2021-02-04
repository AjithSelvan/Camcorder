package com.airbender.camcorder

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.ar.sceneform.rendering.CameraProvider
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

class ViewModelCamera(application:Application) : AndroidViewModel(application){
    private val getProcessCameraProvider by lazy {
        MutableLiveData<ProcessCameraProvider>().apply {
            val cameraProviderFuture=ProcessCameraProvider.getInstance(getApplication())
            cameraProviderFuture.addListener(
                {
                    try{
                        value=cameraProviderFuture.get()

                    }catch (exc :ExecutionException){
                        throw IllegalStateException("failed to retrieve camera process",exc)
                    }catch (exc:InterruptedException){
                        throw IllegalStateException("failed to retrieve camera process",exc)
                    }
                },ContextCompat.getMainExecutor(getApplication())
            )
        }
    }
    val processCameraProvider:LiveData<ProcessCameraProvider>
        get() = getProcessCameraProvider
}