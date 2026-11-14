package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nova AI", appName)
  }

  @Test
  fun `default model is free Hermes 3`() {
    assertEquals(
      "nousresearch/hermes-3-llama-3.1-405b:free",
      com.example.data.remote.AiModelCatalog.DEFAULT_MODEL.id
    )
  }
}
