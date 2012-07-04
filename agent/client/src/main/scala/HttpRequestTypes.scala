
// request type implicits for the http client

package htrcagentclient

trait RequestTypes {
  
  trait ReqType {   
    def str: String                                                                     
  }
  
  implicit def reqTypeToStr(rt: ReqType): String = rt.str
  
  case object GET extends ReqType {
    def str = "GET"
  }
  case object PUT extends ReqType {
    def str = "PUT"
  }
  case object POST extends ReqType {
    def str = "POST"
  }
  case object DELETE extends ReqType {
    def str = "DELETE"
  }
  
}
