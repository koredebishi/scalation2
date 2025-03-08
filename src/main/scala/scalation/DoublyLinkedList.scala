
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Korede Bishi
 *  @version 2.0
 *  @date    Sun Feb 25 20:55:28 EST 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Data Structure: Doubly Linked List
 */

package scalation

import scala.collection.mutable.AbstractIterable
import scala.reflect.ClassTag


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `DoublyLinkedList` class provides a data structure implementing mutable
 *  doubly-linked lists.
 *      next                    -->    -->
 *      tail (last car) --> [e1]   [e2]   [e3] <-- head (lead car)
 *      prev                    <--    <--
 *  @param A  the type of the elements/values in the list
 */
class DoublyLinkedList [A: ClassTag]
    extends AbstractIterable [A]
        with Serializable:

    private val debug = debugf ("DoublyLinkedList", true)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `Node` inner case class wraps elements in nodes for double linkage.
     *  @param elem  the element
     *  @param prev  the predecessor node (car ahead)
     *  @param next  the successor node (car behind)
     */
    case class Node (elem: A, var prev: Node, var next: Node):

        override def toString: String = s"Node ($elem)"

    end Node

    private var head_ : Node = null                                 // head node (first car)
    private var tail_ : Node = null                                 // tail node (last car)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `NodeIterator` inner class supports iterating over all the nodes in this list.
     *  @param ns  the starting node (defaults to tail)
     */
    class NodeIterator (ns: Node = tail_) extends Iterator [Node]:
        var n = ns
        def hasNext: Boolean = n != null
        def next (): Node = { val n_ = n; n = n.next; n_ }
    end NodeIterator

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return an iterator for retrieving all the nodes in this list.
     *  @see scala.collection.IterableOnce
     */
    def nodeIterator: Iterator [Node] = new NodeIterator ()

    def getPrev (n: Node): Node = n.prev

    def getNext (n: Node): Node = n.next

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `ListIterator` inner class supports iterating over all the elements in this list.
     *  @param ns  the starting node (defaults to tail)
     */
    class ListIterator (ns: Node = tail_) extends Iterator [A]:
        var n = ns
        def hasNext: Boolean = n != null
        def next (): A = { val n_ = n; n = n.next; n_.elem }
    end ListIterator

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return an iterator for retrieving all the elements in this list.
     *  @see scala.collection.IterableOnce
     */
    def iterator: Iterator [A] = new ListIterator ()

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Retrieve the element in node n (e.g., the current car).
     *  @param n  the node containing the sought element
     */
    def elemAt (n: Node): A = n.elem

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the head/first node in the list (e.g, node holding the first car).
     */
    override def head: A = head_.elem

    def headNode: Node = head_


    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the tail/last node in the list (e.g, node holding the last car).
     */
    override def last: A = tail_.elem

    def lastNode: Node = tail_

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return whether the list is empty (head and tail are null).
     */
    override def isEmpty: Boolean = head_ == null

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add the first element to an empty list and return the new node n.
     *  @param elm  the element to be added
     *  @return the new node added n
     */
    def addFirst (elm: A): Node =
        val n = Node (elm, null, head_)                             // new node has no predecessor and its next is the current head
        if head_ != null then                                       // if list is not empty
            head_.prev = n                                          // update the previous head's prev to point to the new node
        head_ = n                                                   // update head to point to the new node
        if tail_ == null then                                       // if the list was empty (tail is null)
            tail_ = n                                               // set tail to the new node
        n
    end addFirst

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add the new element into the list AFTER the given predecessor node `pn` and
     *  return the new node `n`.
     *  Relink:  _pn_ <-> n  <-> nn
     *  @param elm  the element to be added
     *  @param pn   the predecessor node (defaults to head if not given)
     *  @return the new node `n`
     */
    def addAfter (elm: A, pn: Node = head_): Node =
        if pn == null || isEmpty then
            addFirst (elm)
        else
            val nn = pn.next                                        // successor node nn
            val n = Node (elm, pn, nn)                              // make a new node n
            pn.next = n                                             // link forward
            if nn != null then nn.prev = n                          // link backward

            if pn == head_ then head_ = n                           // if pn was head, reset to n
            debug ("addAfter", s"pn = $pn, n = $n, nn = $nn")
            n
    end addAfter

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add the new element into the list BEFORE the given successor node `nn` and
     *  return the new node `n`.
     *  Relink:  pn <-> _nn_  TO  pn <-> n <-> _nn_
     *  @param elm the element to be added
     *  @param nn  the successor(next) node (defaults to tail if not given)
     *  @return the new node `n`
     */
    def add (elm: A, nn: Node = tail_): Node =
        if nn == null || isEmpty then
            addFirst (elm)
        else
            val pn = nn.prev                                        // predecessor node pn
            val n = Node (elm, pn, nn)                              // make a new node n
            nn.prev = n                                             // link backward
            if pn != null then pn.next = n                          // link forward

            if nn == tail_ then tail_ = n                           // if nn was tail, reset to n
            debug("add", s"pn = $pn, n = $n, nn = $nn")
            n
    end add

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Remove the node `n` from the linked list.
     *  Relink:  pn <-> n <-> nn  TO  pn <-> nn
     *  @param n the node to remove (unlink)
     */
    def remove (n: Node = head_): Unit =
        val pn = n.prev                                             // predecessor node pn
        val nn = n.next                                             // successor node nn

        if pn != null then pn.next = nn                             // forward bypass of n
        if nn != null then nn.prev = pn                             // backward bypass of n

        if n == head_ then head_ = nn                               // if n was head, reset to nn
        if n == tail_ then tail_ = pn                               // if n was tail, reset to pn

        n.prev = null                                               // n no longer links
        n.next = null
        debug ("remove", s"pn = $pn, nn = $nn")
    end remove

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add the new element BEFORE the given successor node `nn` and return the new node `n`.
     *  Relink:  pn <-> nn  TO  pn <-> n <-> nn
     *  The predecessor (`pn`) of the successor node `nn` is relinked to point to the new node `n`.
     *  Similarly, the new node `n` links back to `pn` and forward to `nn`. If `nn` is `null`,
     *  this method adds the element as the first element in the list.
     *  @param elm the element to be added
     *  @param nn  the successor node (defaults to `null` if not provided)
     *  @return the newly created node `n` inserted before node `nn`
     */
    def addBefore (elm: A, nn: Node): Node =
        if nn == null then addFirst (elm)                           // if nn is null, add as the first element

        val n = Node (elm, nn.prev, nn)                             // create the new node n with links to pn and nn
        if nn.prev != null then                                     // if there is a predecessor, link it to n
            nn.prev.next = n
        nn.prev = n                                                 // link nn back to the new node n
        debug ("addBefore", s"elm = $elm, inserted before = ${nn.elem}")
        n
    end addBefore

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Clear the list of all nodes (and their elements).
     */
    def clear (): Unit = { tail_ = null; head_ = null }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Convert this doubly linked list to a string.
     */
    override def toString (): String =
        val sb = StringBuilder ("DoublyLinkedList (tail -")
        for n <- nodeIterator do sb.append (s"> [ $n ] <-")
        sb.append (" head)").mkString
    end toString

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Convert the elements of this doubly linked list to a Scala List.
     *  This method is efficient in terms of maintaining the correct order without
     * nneeding a separate reverse at the end.
     */
    override def toList: List[A] =
        val buf = new scala.collection.mutable.ListBuffer [A]()     // use ListBuffer for efficient appends
        for n <- nodeIterator do                                    // traverse using the predefined nodeIterator
            buf += n.elem
        buf.toList                                                  // convert ListBuffer to List
    end toList

end DoublyLinkedList


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `doublyLinkedListTest` main function tests the `DoublyLinkedList` class.
 *  > runMain scalation.doublyLinkedListTest
 */
@main def doublyLinkedListTest (): Unit =

    banner ("Test the add method")
    val dll = DoublyLinkedList [Int] ()
    for i <- 0 until 10 do dll.add (i)
    val n = dll.headNode
    println (s"n = $n")
    println (dll.getNext (n))

    banner ("Test the addAfter method")
    dll.clear ()
    for i <- 0 until 10 do dll.addAfter (i)

    //    bannern ("Test the addBefore method")
    //    dll.clear ()
    //    val initialNode = dll.addFirst (10)            // start by adding an initial node to reference
    //    for i <- 1 until 10 do
    //        dll.addBefore (i, initialNode)             // add before the initial node

    //    banner (dll.getprev (i))
    //    banner (dll.getnext (i))

    banner ("Test the remove method")
    while ! dll.isEmpty do
        dll.remove ()
        println (dll)

end doublyLinkedListTest

