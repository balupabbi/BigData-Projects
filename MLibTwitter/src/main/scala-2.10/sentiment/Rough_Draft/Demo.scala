package sentiment.Rough_Draft

/**
 * Created by Bhargav on 12/4/16.
 */
object Demo {
  def main(args: Array[String]) {
    val fruit = Set("apples", "oranges", "pears")
    val nums: Set[Int] = Set()


    println( "Head of fruit : " + fruit.head )
    println( "Tail of fruit : " + fruit.tail )
    println( "Check if fruit is empty : " + fruit.isEmpty )
    println( "Check if nums is empty : " + nums.isEmpty )


    def add(fi: Int, si: Int): Int = {
      val sum = fi + si
      return sum

    }

    val a = 9
    val b = 10
    print(add(a,b))


    val h = "123"
    print(toInt(h))

    val (test, he) = {if(h == "123"){print("ahhhhhh")}else{print("nooooo")}
      ("hello", "watup")}

    print("\n", test + "  " + he)

  }


  def toInt(in: String): Option[Int] = {
    try {
      Some(Integer.parseInt(in.trim))
    } catch {
      case e: NumberFormatException => None
    }
  }


}
