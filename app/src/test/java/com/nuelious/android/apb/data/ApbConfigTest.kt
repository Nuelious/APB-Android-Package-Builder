package com.nuelious.android.apb.data
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApbConfigTest {
 private fun config(output:String="out",format:String="APB")=ApbConfig(JSONObject()
  .put("format",format).put("format_version",1)
  .put("project",JSONObject().put("name","Test").put("type","custom"))
  .put("source",JSONObject().put("root","."))
  .put("build",JSONObject().put("engine","custom").put("output",output)))
 @Test fun acceptsSafeConfig(){config().validate()}
 @Test fun rejectsWrongFormat(){assertThrows(IllegalArgumentException::class.java){config(format="wrong").validate()}}
 @Test fun rejectsTraversal(){assertThrows(IllegalArgumentException::class.java){config(output="../outside").validate()}}
 @Test fun rejectsAbsoluteOutput(){assertThrows(IllegalArgumentException::class.java){config(output="/sdcard/out").validate()}}
}
