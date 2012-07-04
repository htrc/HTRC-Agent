
// nonsense dsl stuff for making urls

package htrcagentclient

trait Urls {

  case class Url(path: String) {

    def /(seg: String): Url = {
      if(path.last == '/')
        Url(path + seg)
      else
        Url(path + "/" + seg)
    }

  }

  def :/(root: String): Url = Url(root)

  implicit def urlToStr(url: Url): String = url.path

}
