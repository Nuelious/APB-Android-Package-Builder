package com.nuelious.android.apb.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

class GitHubRepoImporter(private val context:Context) {
 fun download(repositoryUrl:String, branchInput:String="main"):File {
  val uri=Uri.parse(repositoryUrl.trim())
  require(uri.scheme=="https"&&uri.host=="github.com"){"Enter a public https://github.com/owner/repository URL"}
  val parts=uri.pathSegments
  require(parts.size>=2){"Repository URL must contain owner and repository"}
  val owner=parts[0];val repo=parts[1].removeSuffix(".git")
  require(owner.matches(Regex("[A-Za-z0-9-]+"))&&repo.matches(Regex("[A-Za-z0-9._-]+"))){"Invalid repository URL"}
  val branch=branchInput.trim().ifBlank{"main"}
  require(branch.length<=120&&!branch.startsWith("/")&&!branch.contains("..")){"Invalid branch name"}
  val encoded=URLEncoder.encode(branch,"UTF-8").replace("+","%20")
  val url=URL("https://api.github.com/repos/$owner/$repo/zipball/$encoded")
  val connection=(url.openConnection() as HttpURLConnection).apply {
   requestMethod="GET";connectTimeout=20000;readTimeout=60000;instanceFollowRedirects=true
   setRequestProperty("User-Agent","APB-Android-Package-Builder")
   setRequestProperty("Accept","application/vnd.github+json")
  }
  val zip=File(context.cacheDir,"github-$owner-$repo.zip")
  try {
   val code=connection.responseCode
   require(code in 200..299){"GitHub returned HTTP $code. Check repository visibility, URL and branch."}
   connection.inputStream.use{input->zip.outputStream().use{input.copyTo(it)}}
  } finally { connection.disconnect() }
  val stage=File(context.cacheDir,"github-import-$owner-$repo").apply{deleteRecursively();mkdirs()}
  var total=0L;var entries=0
  try {
   ZipInputStream(zip.inputStream().buffered()).use{input->while(true){
    val entry=input.nextEntry?:break;entries++;require(entries<100000){"Repository archive has too many files"}
    val out=File(stage,entry.name).canonicalFile
    require(out.path.startsWith(stage.canonicalPath+File.separator)||out==stage){"Unsafe archive path"}
    if(entry.isDirectory)out.mkdirs() else {
     out.parentFile?.mkdirs()
     out.outputStream().use{stream->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<3L*1024*1024*1024){"Repository expands beyond 3 GiB"};stream.write(buffer,0,n)}}
    }
    input.closeEntry()
   }}
   val roots=stage.listFiles()?.filter{it.isDirectory}?:emptyList()
   require(roots.size==1){"Unexpected GitHub archive layout"}
   return roots.first()
  } catch(e:Exception){stage.deleteRecursively();throw e}
 }
}
