
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  Jacobi Coleman
 *  @version 2.0
 *  @date    Wed May  1 01:19:46 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Example Animation of a Road
 */

package scalation
package scala3d

import javafx.scene.input.{MouseEvent, ScrollEvent}

import scala.collection.mutable.ArrayBuffer

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.geometry.Point3D
import scalafx.scene.{Group, PerspectiveCamera, Scene}
import scalafx.scene.paint.{Color, PhongMaterial}
import scalafx.scene.paint.Color.sfxColor2jfx
import scalafx.scene.shape.{Box, Cylinder}
import scalafx.scene.shape.Cylinder.sfxCylinder2jfx
import scalafx.scene.shape.Box.sfxBox2jfx
import scalafx.scene.shape.Shape3D.sfxShape3D2jfx
import scalafx.util.Duration

import scalation.mathstat.Statistic

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Road3d` object implements a simple road animation.
 *  > runMain scalation.scala3d.Road3d
 */
object Road3d extends JFXApp3:

    println ("Running Road3d Animation")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `Direction` enumeration indicates the directions that cars can move.
     */
    enum Direction:
        case South
    end Direction

    private val clock       = new Clock ()
    private val roadLength  = 400
    private val roadColor   = Color.LightGrey
    private val lightColor  = Color.Red
    private val lightColor2 = Color.Green

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a 3D scene and start the animation.
     */
    override def start (): Unit =
        val root = new Group ()                                // root node for scene 

        // create sources and add to root
        val source = List ( new Source (15, "northSpawn", Point3D (390, 0, -25)) {})
        source.foreach { s => root.children.add (s.entry) }

        // create sinks and add to root
        val sink = List ( new Sink (15, "fromNorth", Point3D (390, 840, -25)) {})
        sink.foreach { s => root.children.add (s.exit) }

        // create roads and add to root
        val roads = List (
            new Box (15, roadLength, 5) {                      // from north-in position
                translateX = 390; translateY = 200 },
            new Box (15, roadLength, 5) {                      // from south-out position
                translateX = 390; translateY = 640 })

        roads.foreach { road =>
            val roadMaterial = new PhongMaterial ()
            roadMaterial.diffuseColor = roadColor
            road.material = roadMaterial
            root.children.add (road) }

        // create traffic lights - for pedestrian crossing
        val nslights = List (
            new Cylinder (8, 4) {
                translateX = 390; translateY = 405; translateZ = -25 })

        nslights.foreach { light => 
            light.material = new PhongMaterial (lightColor)
            root.children.add (light) }

        // create a timeline for changing light colors
        val lightsTimeline = new Timeline {
            // Initialize light colors
            var stopColor = lightColor
            var goColor = lightColor2

            // create key frames
            keyFrames = Seq (
                KeyFrame (Duration (2000), onFinished = _ => {
                    // Toggle light colors
                    nslights.foreach { light =>
                        val lightMaterial = new PhongMaterial ()
                        lightMaterial.diffuseColor = stopColor
                        light.material = lightMaterial }
                    val tempColor = stopColor
                    stopColor = goColor
                    goColor = tempColor }),
    
                KeyFrame (Duration (8000))        // add a keyframe with no action, just to create a loop
            ) // end keyFrames
    
            cycleCount = Timeline.Indefinite
    
            val northcars = ArrayBuffer [Vehicle] ()
    
            //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
            /** The cars to add to the animation scene.
             *  @param cars       the cars to be added
             *  @param direction  direction of motion
             */
            def addCarToList (cars: ArrayBuffer [Vehicle], direction: Direction): Unit =
                val carColor = Color.Blue
                direction match
                case Direction.South =>
                    val car = new Vehicle (clock, carColor)
                    car.body.translateX = (source(0).entry.translateX.value);
                    car.body.translateY = (source(0).entry.translateY.value)
                    northcars += car
                    root.children.add (car.body)
            end addCarToList
    
            // add more cars as needed
            var car = new Vehicle (clock)
            car.body.translateX = (source(0).entry.translateX.value);
            car.body.translateY = (source(0).entry.translateY.value)
            northcars += car
    
            northcars.foreach { car => root.children.add (car.body)}
    
            val addCarsTimeline = new Timeline {
                keyFrames = Seq (
                    KeyFrame (Duration (3000), onFinished = _ => {
                        addCarToList (northcars, Direction.South) }))
                cycleCount = Timeline.Indefinite }
    
            addCarsTimeline.play ()
    
            val nmaterial = nslights(0).material.value.asInstanceOf [javafx.scene.paint.PhongMaterial]
    
            //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
            /** Determine whether the given car should stop at the traffic light.
             *  @param car        the car that may need to stop
             *  @param direction  direction of motion
             */
            def shouldStop (car: Vehicle, direction: Direction): Boolean =
                direction match
                    case Direction.South => nmaterial.getDiffuseColor == sfxColor2jfx (goColor)
                        && car.body.translateY.value + 2 >= nslights(0).translateY.value -15
                        && car.body.translateY.value + 2 <= nslights(0).translateY.value - 10
            end shouldStop
    
            // create a timeline for car animation
            val timeline = new Timeline {
                keyFrames = Seq (
                    KeyFrame (Duration (30), onFinished = _ => {
                        northcars.foreach { car =>
                            if car.body.translateY.value >= sink(0).exit.translateY.value then
                                car.body.translateY = sink(0).exit.translateY.value     // car reached the sink box, stop
                                sink(0).terminate (car, clock)
                            else if shouldStop (car, Direction.South) then
                                car.body.translateY = car.body.translateY.value
                            else
                                car.body.translateY = car.body.translateY.value + 2
                            end if
                        } // end northcars
                    }) // end KeyFrame
                 ) // end keyFrames
                 cycleCount = Timeline.Indefinite
            } // end timeline
            timeline.play ()

        } // end lightsTimeline
        lightsTimeline.play ()

        println (Statistic.labels)
        for s <- sink do println (s.showStats ())

        // create and position the camera
        val camera = new PerspectiveCamera (false)
        camera.translateZ   = -400
        camera.translateY   = 50
        camera.translateX   = -300
        camera.rotationAxis = new Point3D (400, 0, 0)
        camera.rotate       = 67

        // camera movement variables
        var anchorX, anchorY, anchorPosX, anchorPosY = 0.0

        // mouse drag event handler for camera rotation
        root.onMousePressed = (event: MouseEvent) => {
            anchorX    = event.getSceneX
            anchorY    = event.getSceneY
            anchorPosX = camera.translateX.value
            anchorPosY = camera.translateY.value
            println ("mouse is pressed") }

        // mouse dragged event handler
        root.onMouseDragged = (event: MouseEvent) => {
            camera.translateX.value = anchorPosX - (anchorY - event.getSceneY) / 5.0
            camera.translateY.value = anchorPosY + (anchorX - event.getSceneX) / 5.0
            println ("mouse is pressed and dragged") }

        // mouse scroll event handler for camera zooming
        root.onScroll = (event: ScrollEvent) => {
            val delta = event.getDeltaY
            camera.translateZ.value -= delta
            println ("scroll wheel is used") }

        // create the main scene
        val mainScene    = new Scene (root, 1000, 800, Color.White)
        mainScene.camera = camera

        // set the stage
        stage = new PrimaryStage { title = "Traffic 3D"; scene = mainScene }
    end start

end Road3d

