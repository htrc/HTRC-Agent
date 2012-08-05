
// case class and helpers for managing an "application/zip" response

package httpbridge

import com.ning.http.client._
import java.io._
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import scala.collection.mutable.ArrayBuffer

// make a tree datatype to hold directories and files
// recursive function to write to folder

// todo : check if the directories/files exist before writing

trait ZipTree {
  def toFolder(dir: String)
}

case class Directory(name: String, contents: List[ZipTree]) extends ZipTree {
  def toFolder(dir: String) {
    new File(dir+"/"+name).mkdir
    contents.foreach(_.toFolder(dir+"/"+name))
  }
}  

case class ZFile(name: String, contents: ArrayBuffer[Byte]) extends ZipTree {
  override def toString: String = "File("+name+","+contents.length+")"
  def toFolder(dir: String) {
    val out = new java.io.FileOutputStream(dir+"/"+name)
    out.write(contents.toArray)
    out.close()
  }
}

// the raw class that knows about the input stream
case class Zip(input: ZipInputStream) {

  // read from the stream and construct entries
  def decodeChunk:ArrayBuffer[Byte] = {
    val bytes = new ArrayBuffer[Byte]
    val buffer = new Array[Byte](2048)
    var size = input.read(buffer, 0, 2048)
    while(size != -1) {
      bytes ++= buffer.take(size)
      size = input.read(buffer, 0, 2048)
    }
    bytes
  }

  // pure functional code to decode the zip stream into a tree
  // style is to use an Either type to allow backtracking
  // initial dir should be ""
  // RISK: on *very* large zip streams this might blow the callstack
  def decodeStream(dir: String, entry: ZipEntry = input.getNextEntry): List[Either[ZipTree,ZipEntry]] = {

    // case 1: directory in this level
    // case 2: file in this level
    // case 3: directory up a level
    // case 4: file up a level

    // to make cases 3 and 4 detectable must include "current directory"

    if(entry != -1 && entry != null) {
      
      // now that we know an entry exists, get information
      val rawName = entry.getName.split('/')
      val isDir = entry.isDirectory
      val parentDir = if(rawName.length > 0) rawName.reverse.drop(1).reverse.mkString("/") else ""
      val name = rawName.last

      // todo: clean collect lines
      if(parentDir == dir && isDir) {
        val ndir = if(dir == "") name else dir+"/"+name
        val res = decodeStream(ndir)

        val lefts = res.collect((x:Either[ZipTree,ZipEntry]) => 
          x match { case Left(t) => t })
        val rights = res.collect((x:Either[ZipTree,ZipEntry]) => 
          x match { case Right(e) => decodeStream(dir, e) }).flatten
        
        Left(Directory(name, lefts)) :: rights
       
      } else if(parentDir != dir && isDir) {
        Right(entry) :: Nil
      } else if(parentDir == dir && !isDir) {
        val chunk = decodeChunk
        Left(ZFile(name, chunk)) :: decodeStream(dir)
      } else {
        Right(entry) :: Nil
      }
    } else {
      Nil: List[Left[ZipTree, ZipEntry]]
    }

  }

  val rawTree = decodeStream("")
  val lefts = rawTree.collect( e => e match { case Left(v) => v } )
  val tree = lefts // todo: figure out how to sneak a dir name in here

  // just call toFolder on each element at the top level of the tree
  def toFolder(dir: String) = 
    tree map { _.toFolder(dir) }

}
