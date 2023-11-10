
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  Yulong Wang, John Miller
 *  @version 2.0
 *  @date    Tue Oct  3 19:40:51 EDT 2023
 *  @see     LICENSE (MIT style license file).
 *
 *  @title   Base Model Class for Agent-Based Simulation
 */

package scalation
package simulation.agent

import java.util.concurrent.Semaphore

import scala.collection.mutable
import scala.collection.mutable.{LinkedHashMap, PriorityQueue, ArrayBuffer as VEC}

import scalation.animation.{AnimateCommand, CommandType}
import scalation.animation.CommandType.MoveToken
import scalation.database.Identifiable
import scalation.database.graph.{EdgeType, PGraph, VertexType}
import scalation.mathstat.{StatTable, Statistic, VectorD}
import scalation.simulation.{Completion, Coroutine}
import scalation.scala2d.Colors._
import scalation.scala2d.Shape

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Model` class maintains a property graph making up the model and
 *  controls the flow of entities (`SimAgent`s) through the model, following the
 *  agent-based simulation world-view.  It maintains a time-ordered priority queue
 *  to activate/re-activate each of the entities.  Each entity (`SimAgent`) is
 *  implemented as a `Coroutine` and may be thought of as running in its own thread.
 *  @param _name      the name of this simulation model
 *  @param reps       the number of independent replications
 *  @param startSim   the start time of this simulation
 *  @param animating  whether to animate the model
 *  @param aniRatio   the ratio of simulation speed vs. animation speed
 */
class Model (_name: String,val reps: Int = 1, startSim: Double = 0.0,
             animating: Boolean = true, aniRatio: Double = 10.0,
             width: Int = 800, height: Int = 600)
         extends Identifiable (_name)
         with Completion:

    protected val graphMod = PGraph (name, Model.vertexTypes, Model.edgeTypes,
                                     animating, aniRatio, width, height)      // the graph model

    private val debug  = debugf ("Model", false)                     // debug function
    private val flaw   = flawf ("Model")                             // flaw function
    private val agenda = PriorityQueue.empty [SimAgent]              // time-ordered activation list
    private val sema = new Semaphore(0)

    private [agent] var clock      = startSim                        // the simulation clock
    private [agent] var simulating = false                           // the simulation clock
    private [agent] val log        = Monitor ("simulation")          // log for model execution
    private [agent] var nAgents = 0                                  // current number of live agents
    private [agent] val statList = VEC[Statistical]()

    var rep = 1                                                      // which rep currently is

    val director = this

    debug ("init", s"name = $name, startSim = $startSim")

    /** The map of statistics vectors records the means of each replication
     */
    val statV = mutable.LinkedHashMap [String, VectorD] ()

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Reset the agenda and stateful components for next replication.
     */
    def reset(): Unit =
        while !agenda.isEmpty do agenda.dequeue ()                   // clean out actors from agenda
        // reset stateful components
        for waitQueue <- WaitQueue.verts do waitQueue.asInstanceOf [WaitQueue].clear ()
        for waitQueue <- WaitQueue_LCFS.verts do waitQueue.asInstanceOf [WaitQueue_LCFS].clear ()
    end reset

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Reset and aggregate all statistics.
     *  @param rep   the current replication (1, ... reps)
     *  @param rmax  the maximum number of replications/batches
     */
    def resetStats(rep: Int, rmax: Int = reps): Unit =
        if rep == 1 then
            for stat <- getStatistics do statV += stat.name -> new VectorD (rmax)
        end if
        for stat <- getStatistics do
            statV(stat.name)(rep - 1) = stat.mean
            stat.reset ()
        end for
    end resetStats

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Execute the simulation (includes scheduling all Sources) returning summary
     *  statistics.
     */
    def simulate (): Unit =
        banner (s"start simulation $name at $startSim")
        if  rep == 1 then graphMod.print ()                          // to be tuned
        if  rep == 1 && animating then graphMod.display (100000)     // FIX - should be adaptive
//      return                                                       // end before simulating to only examine initial graph
        log.trace (this, "starts", this, clock)
        for source <- Source.sources do
            source.time = clock
            schedule (source)                                        // put all sources on agenda
        end for
        while rep <= reps do
            simulating = true
            yield2Next (null)
            sema.acquire ()                                          // waits for all the virtual thread finish
            fini (rep)
            if rep < reps then reset ()                              // reset for next replication
            resetStats (rep)                                         // reset and aggregate statistics
            rep += 1
        end while
        if reps > 1 then reportV ()
    end simulate

    private val timeDelayR = 900                                     // 15 minutes * 60 FIX - generalize

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `Reporter` inner class observes the statistic at a specific time so schedule this reporter.
     *  @param actTime  the activation for the reporter.
     */
    class Reporter (actTime: Double = clock) extends SimAgent ("reporter", actTime, this):
        def act (): Unit =
            customReport (statList)
            director.yield2Next (this, true)
        end act
    end Reporter

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Re-simulate over the same components multiple times with different input(source);
     *  this is not the same as replication, replication input(source) is the same
     *  @param firstTime  whether it is the first time
     *  @param simStart   the time of simulation start
     */
    def resimulate (firstTime: Boolean, simStart: Double = 0.0): Unit =
        clock      = simStart
        val myreport = Reporter (clock + timeDelayR)
        schedule (myreport)
//      if firstTime && rep == 1 then graphMod.print ()              // to be tuned
        if firstTime && rep == 1 && animating then graphMod.display (100000)
        log.trace (this, "starts", this, clock)

        if firstTime then
            for source <- Source.sources do
                source.time = clock
                schedule (source)                                    // put all sources on agenda
            end for
        else
            for source <- Source.sources do
                source.resetStart ()
                source.time = simStart
                schedule (source)
//              reschedule (source)
            end for
        end if

        while rep <= reps do
            simulating = true
            yield2Next (null)
            sema.acquire ()
            //fini (rep)                                             // no need to print for now
            //if rep < reps then reset ()                            // reset for next replication
            reset ()                                                 // for re-simulating
            Coroutine.waitThreadFinish ()                            // clean the buffer
            resetStats (rep)                                         // reset and aggregate statistics
            rep += 1
        end while
        if reps > 1 then reportV ()
        rep = 1                                                      // reset
    end resimulate

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Finish by producing statistical reports and optionally animation.
     *  Typically animation and reports in pop up window turned off for high
     *  replications and/or simulation optimization.
     *  @param rep  the replication number (1, ... reps)
     */
    protected def fini (rep: Int): Unit =
        report ()                                                    // report in terminal
//      if reps> 1 then reportV ()
        if animating then
            reportF ()                                               // report in new window/frame
//          if rep == 1 then dgAni.animate (0, 100000)               // only animate first rep
//          dgAni.saveImage (DATA_DIR + name + ".png")
        end if
    end fini

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Report on the statistical results of the simulation in a new GUI window/frame.
     */
    protected def reportF (): Unit = new StatTable (s"$name statistics", getStatistics)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Schedule the agent to act (be activated) at agent.time (optionally delayed).
     *  @param agent  the agent to be scheduled
     *  @param delay  the amount of time to delay the agent's activation time
     */
    def schedule (agent: SimAgent, delay: Double = 0.0): Unit =
        if delay < 0.0 then
           flaw ("schedule", s"agent $agent delay time is negative: $delay")
           banner ("WARN")
        end if
        agent.time += delay
//      agent.time = clock + delay
        if agent.time < clock then                                   // out of order scheduling => WARN
           flaw ("schedule", s"agent $agent activation time < $clock")
           banner ("WARN")
        end if
//      debug ("schedule", s"now = $clock: schedule agent $agent")
        log.trace (this, "schedules agent", agent, clock)
        agenda += agent
    end schedule

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Reschedule the agent to act (be activated) at agent.time (optionally delayed)
     *  for those agents ahead of director.clock.
     *  @param agent  the agent to be rescheduled
     *  @param delay  the amount of time to delay the agent's activation time
     */
    def reschedule (agent: SimAgent, delay: Double = 0.0): Unit =
        log.trace (this, "reschedules agent", agent, clock)
        agent.time = clock + delay
        agenda += agent
    end reschedule

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Yield to the next agent, i.e., agent1 -- yield-to -> agent2.
     *  @param agent1  the currently executing agent
     *  @param quit    whether agent1 wants to quit/terminate
     */
    def yield2Next (agent1: SimAgent, quit: Boolean = false): Unit =
        if agenda.nonEmpty then
            val agent2 = agenda.dequeue ()                           // get agent2 from the agenda
            if agent2.time < clock then                              // out of order execution => QUIT
                flaw ("y2n", s"agent $agent2 activation time < $clock")
                println (s"this is ${agent2.time}, $clock")
//              reschedule (agent2)
                schedule (agent2)
                banner (s" QUIT this is ${agent2.time} < $clock")
                return
            end if

            clock = agent2.time                                      // advance the time
//          debug ("yield2Next", s"${this.me} resumes ${agent2.me} at $clock")

            if agent1 == null then
                log.trace (this, "starts", agent2, clock)
                agent2.start ()                                      // source needs start first and start this, if there are multiple sources
            else
                log.trace (agent1, "resumes", agent2, clock)
                if quit && !agent1.isInstanceOf [Gate] then nAgents -= 1 // decrement the number of live agents
                agent1.yyield (agent2, quit)

        else                                                         // the last coroutine
            log.trace (this, "stops", agent1, clock)
            agent1.yyield (null,quit)
            if rep < reps then
                simulating = false
                agenda.clear ()
                hasFinished ()
            else
                finishedup ()
            end if
            sema.release ()                                          // main thread continue
        end if
    end yield2Next

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Finish up the simulation.
     */
    def finishedup (): Unit =
        log.trace (this, s"ends", null, clock)
//      cleanup ()
        reset ()
        log.trace (this, "terminates model", null, clock)
        simulating = false
        if animating then graphMod.setAniDone ()
        hasFinished ()                                               // signal via semaphore that simulation is finished
//      yyield (null, true)                                          // yield and terminate the director
    end finishedup

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Put a token command (CreateToken, MoveToken or DestroyToken) on the animation queue.
     *  @param agent  who is being animated
     *  @param what   what animation command
     *  @param color  the color the token
     *  @param shape  the shape of the token
     */
    def animate (agent: SimAgent, what: CommandType, color: Color = null,
                 shape: Shape = null): Unit =
        var eid   = agent.id
        if agent.isInstanceOf [Gate] then eid += 1                   // FIX - Gate's vertex is one more
        val label = agent.name
        val apos  = if what == MoveToken then agent.pos(0 to 2)      // agent's position (x, y)
                    else agent.pos                                   // (x, y, w, h)
//      debug ("animate", s">>> $label.$eid, $what, $color, $shape, $apos")
        if animating then graphMod.add_aniQ (AnimateCommand (what, eid, shape, label, true, color,
                                             apos.toArray, clock))
    end animate

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compare the order of agents based on their activation times.
     *  @param agent  the first agent in comparison
    private def orderedAgent (agent1: SimAgent): Ordered [SimAgent] =
        new Ordered [SimAgent]
            { def compare (agent2: SimAgent) = agent2.time compare agent1.time }
    end orderedAgent
     */

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the statistical results of the simulation (statistics for each vertex).
     *  Includes both sample and time-persistent statistics.
     */
    def getStatistics: VEC [Statistic] =
        val stats = VEC [Statistic] ()
        for stat <- statList do
//          if reps == 1 && !stat.isInstanceOf [Transport] then
            stat.addStats (stats)
        stats
    end getStatistics

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Report on the statistical results of a simulation run.
     */
    private def report (): Unit =
        println (Statistic.line)
        println (Statistic.labels)
        println (Statistic.line)
        for stat <- statList do
            println (stat.durationStat)
            if ! stat.isInstanceOf [Source] && ! stat.isInstanceOf [Sink] && ! stat.isInstanceOf [Gate] then
                println (stat.persistentStat)
            end if
        println (Statistic.line)
//      customReport (statList)
    end report

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Report (custom) on the statistical results of a simulation run.
     *  @param statList  the list of statistics
     */
    def customReport (statList: VEC [Statistical]): Unit =
        println ("user definable custom report")
    end customReport

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Report on the statistical results of the overall simulation as recorded
     *  in statV (may include multiple replications/batches).
     *  @param showMeans  whether to show the individual run/batch means
     */
    protected def reportV (showMeans: Boolean = false): Unit =
        println (Statistic.line)
        println (Statistic.labels)
        println (Statistic.line)
        for (k, v) <- statV do
            val aStat = new Statistic(k)
            aStat.tallyVec(v)
            println(aStat)
        end for
        println (Statistic.line)
    end reportV

end Model


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Model` companion object provides a shutdown method and methods to add
 *  vertex/edge types to the model.
 */
object Model:

    private val vertexTypes = VEC [VertexType] ()                    // collection of vertex types
    private val edgeTypes   = VEC [EdgeType] ()                      // collection of edge types

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add vertex type vt to the collection of vertex types.
     *  @param vt  the vertex type to add
     */
    def add (vt: VertexType): Unit = vertexTypes += vt

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add edge type et to the collection of edge types.
     *  @param et  the edge type to add
     */
    def add (et: EdgeType): Unit = edgeTypes += et

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Shutdown the Model execution infrastructure (WARNING: this method should
     *  only be called right before program termination).  Make sure all threads
     *  have finished (e.g., call `waitFinished`), not just the main thread.
     *  If `shutdown` is not called, the application may hang.
     */
    def shutdown (): Unit = Coroutine.shutdown ()

end Model

