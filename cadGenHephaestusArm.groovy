import com.neuronrobotics.sdk.common.BowlerAbstractDevice

import com.neuronrobotics.bowlerstudio.creature.ICadGenerator;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.creature.CreatureLab;
import org.apache.commons.io.IOUtils;
import com.neuronrobotics.bowlerstudio.vitamins.*;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.IDeviceAddedListener
import com.neuronrobotics.sdk.common.IDeviceConnectionEventListener

import java.nio.file.Paths;

import eu.mihosoft.vrl.v3d.CSG
import eu.mihosoft.vrl.v3d.Cube
import eu.mihosoft.vrl.v3d.Cylinder
import eu.mihosoft.vrl.v3d.Parabola
import eu.mihosoft.vrl.v3d.RoundedCube
import eu.mihosoft.vrl.v3d.RoundedCylinder
import eu.mihosoft.vrl.v3d.Sphere
import eu.mihosoft.vrl.v3d.Transform

import com.neuronrobotics.bowlerstudio.vitamins.*;
import javafx.scene.transform.Affine;
import  eu.mihosoft.vrl.v3d.ext.quickhull3d.*
import eu.mihosoft.vrl.v3d.Vector3d


double grid =25

CSG reverseDHValues(CSG incoming,DHLink dh ){
	//println "Reversing "+dh
	TransformNR step = new TransformNR(dh.DhStep(0))
	Transform move = com.neuronrobotics.bowlerstudio.physics.TransformFactory.nrToCSG(step)
	return incoming.transformed(move)
}

CSG moveDHValues(CSG incoming,DHLink dh ){
	TransformNR step = new TransformNR(dh.DhStep(0)).inverse()
	Transform move = com.neuronrobotics.bowlerstudio.physics.TransformFactory.nrToCSG(step)
	return incoming.transformed(move)
}

return new ICadGenerator(){

		static final int bracketOneKeepawayDistance = 50
	//			private HashMap<String, GearManager>  map= new HashMap<>()
	//			public  GearManager get(DHParameterKinematics b) {
	//				if(map.get(b.getXml())==null) {
	//					map.put(b.getXml(), new GearManager(b))
	//				}
	//				return map.get(b.getXml())
	//			}
	double motorGearPlateThickness = 10
	def thrustBearingSize = "Thrust_1andAHalfinch"
	double centerTheMotorsValue=20;
	double radiusOfGraspingObject=12.5;
	double movingPartClearence =1.5
	double linkThickness = 6
	double linkYDimention = 20;
	double GripperServoYOffset = 35
	def cornerRad=2
	String boltsize = "M5x25"
	HashMap<String,Object> measurmentsHorn = Vitamins.getConfiguration(  "LewanSoulHorn","round")
	def hornKeepawayLen = measurmentsHorn.mountPlateToHornTop
	double centerlineToOuterSurfacePositiveZ = centerTheMotorsValue+movingPartClearence+hornKeepawayLen-1
	double centerlineToOuterSurfaceNegativeZ = -(centerTheMotorsValue+movingPartClearence+linkThickness)
	@Override
	public ArrayList<CSG> generateCad(DHParameterKinematics d, int linkIndex) {
		def vitaminLocations = new HashMap<TransformNR,ArrayList<String>>()
		ArrayList<DHLink> dhLinks = d.getChain().getLinks()
		ArrayList<CSG> allCad=new ArrayList<>()
		int i=linkIndex;
		DHLink dh = dhLinks.get(linkIndex)
		// Hardware to engineering units configuration
		LinkConfiguration conf = d.getLinkConfiguration(i);
		// Engineering units to kinematics link (limits and hardware type abstraction)
		AbstractLink abstractLink = d.getAbstractLink(i);
		// Transform used by the UI to render the location of the object
		Affine manipulator = dh.getListener();
		// loading the vitamins referenced in the configuration
		//CSG servo=   Vitamins.get(conf.getElectroMechanicalType(),conf.getElectroMechanicalSize())
		CSG motorModel=   Vitamins.get(conf.getElectroMechanicalType(),conf.getElectroMechanicalSize())
		CSG linkBuildingBlockRoundCyl = new Cylinder(linkYDimention/2,linkYDimention/2,linkThickness,30)
										.toCSG()
		CSG linkBuildingBlockRoundSqu = new RoundedCube(linkYDimention,linkYDimention,linkThickness)
										.cornerRadius(cornerRad)
										.toCSG()
										.toZMin()
		CSG linkBuildingBlockRound = new RoundedCylinder(linkYDimention/2,linkThickness)
										.cornerRadius(cornerRad)
										.toCSG()
		CSG linkBuildingBlock = CSG.hullAll([
									linkBuildingBlockRound.movey(linkYDimention),
									linkBuildingBlockRound
									])
									.toZMin()
									.movey(-5)
		
	
		
		double zOffset = motorModel.getMaxZ()
		TransformNR locationOfMotorMount = new TransformNR(dh.DhStep(0)).inverse()
		def shaftLocation = locationOfMotorMount.copy()
		def thrustMeasurments= Vitamins.getConfiguration("ballBearing",
			thrustBearingSize)
		def baseCorRad = thrustMeasurments.outerDiameter/2+5
		double servoAllignmentAngle=0
		CSG gripperMotor=null;
		TransformNR locationOfGripperHinge=null
		TransformNR locationOfServo=null 
		def insert=["heatedThreadedInsert", "M5"]
		def insertMeasurments= Vitamins.getConfiguration(insert[0],
			insert[1])
		if(linkIndex==0)
			shaftLocation.translateY(zOffset)
		else
			shaftLocation.translateZ(centerTheMotorsValue)
		vitaminLocations.put(shaftLocation, [
				conf.getShaftType(),
				conf.getShaftSize()
		])
		TransformNR locationOfBearing = locationOfMotorMount.copy().translateY(1)
		if(linkIndex==0) {
			vitaminLocations.put(locationOfBearing, [
				"ballBearing",
				thrustBearingSize
			])
		}
		if(linkIndex==1) {
			def mountBoltOne =locationOfMotorMount.copy()
							.times(new TransformNR().translateZ(centerlineToOuterSurfacePositiveZ+linkThickness)
								.translateY(-bracketOneKeepawayDistance))
			def mountBoltTwo=mountBoltOne.copy()
								.times(new TransformNR()
									.translateY(+20))
							
			vitaminLocations.put(mountBoltOne,["capScrew", boltsize])
			vitaminLocations.put(mountBoltOne.times(new TransformNR().translateZ(-linkThickness-hornKeepawayLen-0.5-insertMeasurments.installLength)),
				insert)
			vitaminLocations.put(mountBoltTwo,["capScrew", boltsize])
			vitaminLocations.put(mountBoltTwo.times(new TransformNR().translateZ(-linkThickness-hornKeepawayLen-0.5-insertMeasurments.installLength)),
				insert)
			
		}
		if(linkIndex==2) {
			double theta = Math.toDegrees(dh.getTheta())
			servoAllignmentAngle = Math.toDegrees(Math.atan2(GripperServoYOffset,dh.getR()))-(90-theta)
			println "Angle of servo offset = "+servoAllignmentAngle+" "+GripperServoYOffset+" "+dh.getR()+" theta "+theta
			double hypot = Math.sqrt(Math.pow(dh.getR(), 2)+Math.pow(GripperServoYOffset, 2))
			double hingeBackset = 40
			locationOfServo=locationOfMotorMount
			.copy()
			.times(
				new TransformNR(0,0,0,new RotationNR(0,0,90))
				)
			//.translateY(-linkYDimention/2)
			.times(
					new TransformNR(0,0,0,new RotationNR(-servoAllignmentAngle,0,0))
					)
			.translateY(-GripperServoYOffset)
			.times(new TransformNR().translateZ(linkYDimention/2))
			
			locationOfGripperHinge=locationOfServo
			.copy()
			.times(new TransformNR().translateY(hypot-hingeBackset))
			
//			.translateY(-hingeBackset*Math.sin(Math.toRadians(servoAllignmentAngle))-8)
//			.translateX(hypot-hingeBackset)
			
			
			
		
			vitaminLocations.put(locationOfGripperHinge,
					["capScrew", boltsize])
			vitaminLocations.put(locationOfGripperHinge.times(new TransformNR().translateZ(-linkYDimention)),
					insert)
			def mountBoltOne = locationOfServo.copy()
							.times(new TransformNR(0,0,0,new RotationNR(0,0,-90)))
							.times(new TransformNR().translateZ(centerlineToOuterSurfacePositiveZ+linkThickness)
								.translateX(-linkYDimention/2)
								.translateY(-5)
								)
			def mountBoltTwo=mountBoltOne.times(new TransformNR().translateY(linkYDimention))
			vitaminLocations.put(mountBoltOne,["capScrew", boltsize])
			vitaminLocations.put(mountBoltOne.times(new TransformNR().translateZ(-linkThickness-insertMeasurments.installLength)),
				insert)
			vitaminLocations.put(mountBoltTwo,["capScrew", boltsize])
			vitaminLocations.put(mountBoltTwo.times(new TransformNR().translateZ(-linkThickness-insertMeasurments.installLength)),
				insert)
			vitaminLocations.put(locationOfServo.times(new TransformNR(0,0,0,new RotationNR(0,180,0))), [
				"hobbyServo",
				"mg92b"
			])
		}

		if(linkIndex!=d.getNumberOfLinks()-1 ){
			LinkConfiguration confPrior = d.getLinkConfiguration(i+1);
			def vitaminType = confPrior.getElectroMechanicalType()
			def vitaminSize = confPrior.getElectroMechanicalSize()
			//println "Adding Motor "+vitaminType
			def motorLocation = new TransformNR(0,0,centerTheMotorsValue,new RotationNR())
			if(linkIndex==0)
				motorLocation=motorLocation.times(new TransformNR(0,0,0,new RotationNR(0,180,0)))
			if(linkIndex==1)
					motorLocation=motorLocation.times(new TransformNR(0,0,0,new RotationNR(0,-90,0)))
			vitaminLocations.put(motorLocation, [
				vitaminType,
				vitaminSize
			])

		}

		//CSG tmpSrv = moveDHValues(servo,dh)

		//Compute the location of the base of this limb to place objects at the root of the limb
		//TransformNR step = d.getRobotToFiducialTransform()
		//Transform locationOfBaseOfLimb = com.neuronrobotics.bowlerstudio.physics.TransformFactory.nrToCSG(step)

		double totalMass = 0;
		TransformNR centerOfMassFromCentroid=new TransformNR();
		def vitamins =[]
		for(TransformNR tr: vitaminLocations.keySet()) {
			def vitaminType = vitaminLocations.get(tr)[0]
			def vitaminSize = vitaminLocations.get(tr)[1]

			HashMap<String, Object>  measurments = Vitamins.getConfiguration( vitaminType,vitaminSize)

			CSG vitaminCad=   Vitamins.get(vitaminType,vitaminSize)
			Transform move = TransformFactory.nrToCSG(tr)
			def part = vitaminCad.transformed(move)
			part.setManipulator(manipulator)
			vitamins.add(part)

			def massCentroidYValue = measurments.massCentroidY
			def massCentroidXValue = measurments.massCentroidX
			def massCentroidZValue = measurments.massCentroidZ
			def massKgValue = measurments.massKg
			//println vitaminType+" "+vitaminSize
			TransformNR COMCentroid = tr.times(
					new TransformNR(massCentroidXValue,massCentroidYValue,massCentroidZValue,new RotationNR())
					)
			totalMass+=massKgValue
			//do com calculation here for centerOfMassFromCentroid and totalMass
		}
		//Do additional CAD and add to the running CoM
		conf.setMassKg(totalMass)
		conf.setCenterOfMassFromCentroid(centerOfMassFromCentroid)
		Transform actuatorSpace = TransformFactory.nrToCSG(locationOfMotorMount)
		def tipCupCircle = linkBuildingBlockRound.movez(centerlineToOuterSurfacePositiveZ)
		def gripperLug = linkBuildingBlockRound.movez(centerlineToOuterSurfaceNegativeZ)
		def actuatorCircle = tipCupCircle.transformed(actuatorSpace)
		def actuatorCirclekw = linkBuildingBlockRoundCyl.movez(centerlineToOuterSurfacePositiveZ).transformed(actuatorSpace)
		def passivLinkLug = gripperLug.transformed(actuatorSpace)
		
		if(linkIndex==1) {
			double braceDistance=-5;
			double linkClearence = 18.5
			def mountMotorSidekw = linkBuildingBlockRoundCyl
										.movez(centerTheMotorsValue)
										.movex(-linkClearence-movingPartClearence)
			def mountMotorSide = linkBuildingBlockRound
										.movez(centerTheMotorsValue)
										.movex(-linkClearence-movingPartClearence)
			def mountPassiveSide = linkBuildingBlockRoundSqu
										.movez(-centerTheMotorsValue-linkThickness)
										.movex(-linkClearence-movingPartClearence)
		   def mountPassiveSideAlligned = linkBuildingBlockRoundSqu
										.movez(centerlineToOuterSurfaceNegativeZ)
										.movex(-linkClearence-movingPartClearence)
			def clearencelugMotorSide = mountMotorSide.movex(-dh.getR()+bracketOneKeepawayDistance)
			def clearencelugMotorSidekw = mountMotorSidekw.movex(-dh.getR()+bracketOneKeepawayDistance)
			def clearencelugPassiveSide = mountPassiveSide.movex(-dh.getR()+bracketOneKeepawayDistance)
			CSG motorLink = actuatorCircle.movez(-0.5).movex(bracketOneKeepawayDistance)
			CSG motorLinkkw = actuatorCirclekw.movez(-0.5).movex(bracketOneKeepawayDistance)
			def bracemountPassiveSideAlligned = linkBuildingBlockRound
													.movez(centerlineToOuterSurfacePositiveZ)
													.movez(-0.5)
													.movey(braceDistance)
													.movex(-linkClearence-movingPartClearence-dh.getR()+bracketOneKeepawayDistance)
			def bracemountMotorSide=actuatorCircle
									.movez(-0.5)
									.movex(bracketOneKeepawayDistance-linkYDimention/2)
									.movey(braceDistance)
										
			def brace = CSG.unionAll([
				bracemountMotorSide,
				bracemountPassiveSideAlligned
				]).hull()
			brace = brace
						.union([
							brace
							.movez(-centerlineToOuterSurfacePositiveZ+centerlineToOuterSurfaceNegativeZ+0.5),
							clearencelugMotorSide,clearencelugPassiveSide
							]
						).hull()
			def passiveSide = mountPassiveSideAlligned.union(passivLinkLug).hull()
			def motorSidePlate = CSG.hullAll([clearencelugMotorSide,mountMotorSide]);
			motorSidePlate=CSG.hullAll([motorSidePlate,motorSidePlate.toZMax().movez(centerlineToOuterSurfacePositiveZ-0.5)])
			def motorSidePlatekw = CSG.hullAll([clearencelugMotorSidekw,mountMotorSidekw]);
			motorSidePlatekw=CSG.hullAll([motorSidePlatekw,motorSidePlatekw.toZMax().movez(centerlineToOuterSurfacePositiveZ-0.5)])
			
			def center = CSG.unionAll([mountPassiveSideAlligned,mountMotorSide,clearencelugMotorSide,clearencelugPassiveSide])
							.hull()
		    CSG motorToCut = Vitamins.get(conf.getElectroMechanicalType(),conf.getElectroMechanicalSize())
							.rotz(180)
							.movez(centerTheMotorsValue)
							.transformed(actuatorSpace)
			CSG MotorMountBracketkw = actuatorCirclekw.movez(-0.5)
							.union(motorLinkkw)
							.hull()
			CSG MotorMountBracket = actuatorCircle.movez(-0.5)
							.union(motorLink)
							.hull()
							.difference(vitamins)
			def FullBracket =CSG.unionAll([center,passiveSide,brace])
							.difference(motorSidePlatekw.getBoundingBox())
							.difference(vitamins)
							.difference(motorToCut)
							.difference(MotorMountBracketkw.getBoundingBox())
			def finalMiddlePlate = motorSidePlate
								.difference(vitamins)
								
			
			finalMiddlePlate.setColor(javafx.scene.paint.Color.GREENYELLOW)
			MotorMountBracket.setColor(javafx.scene.paint.Color.DARKCYAN)
			FullBracket.setColor(javafx.scene.paint.Color.YELLOW)
			MotorMountBracket.setManipulator(manipulator)
			FullBracket.setManipulator(manipulator)
			finalMiddlePlate.setManipulator(manipulator)
			FullBracket.setName("MiddleLinkMainBracket")
			MotorMountBracket.setName("MiddleLinkActuatorBracket")
			finalMiddlePlate.setName("MiddleLinkMiddleBracket")
			allCad.addAll(FullBracket,MotorMountBracket,finalMiddlePlate)
		}
		if(linkIndex==2) {
			CSG objectToGrab = new Sphere(radiusOfGraspingObject,32,16).toCSG()
			objectToGrab=objectToGrab.intersect(objectToGrab.getBoundingBox().movex(-2))
			
			
			def corners =[]
			Transform gripperSpace = TransformFactory.nrToCSG(locationOfServo)
			Transform hinge = TransformFactory.nrToCSG(locationOfGripperHinge)			
			CSG motorToCut = Vitamins.get(conf.getElectroMechanicalType(),conf.getElectroMechanicalSize())
							.rotz(90)
							.movez(centerTheMotorsValue)
							.transformed(actuatorSpace)			
			
			
			
			def servoCube = linkBuildingBlock.toXMax().movez(centerlineToOuterSurfacePositiveZ-0.5).roty(90).transformed(gripperSpace)	
			def rightServoCube = linkBuildingBlock.toZMax().toXMin().movez(-centerlineToOuterSurfaceNegativeZ).roty(-90).transformed(gripperSpace)
			
			def servoBracket = servoCube.union(rightServoCube).hull()
			def supportBracket = rightServoCube.union(passivLinkLug).hull()
			def linkToCup = rightServoCube.union(gripperLug).hull()
			def ActuatorBracket = servoCube.union(actuatorCircle.movez(-0.5)).hull()
									.difference(vitamins)
			
			CSG pincherCup = new  Cylinder(radiusOfGraspingObject/2,5).toCSG()
	
			def pincherBracket = gripperLug.union(pincherCup).hull()
			
			
			double hingeDiameter = 12
			def hingeBarrel = new RoundedCylinder(hingeDiameter/2,linkYDimention)
									.cornerRadius(cornerRad)
									.toCSG()
									.toZMax()
			def hingeLinkHole = new Cylinder(1,linkYDimention).toCSG()
									.toZMax()
									.movex(centerlineToOuterSurfaceNegativeZ*2/3)
			def hingeSlotCutter = new Cube(hingeDiameter*3+1,hingeDiameter*3,linkThickness+movingPartClearence/2).toCSG()
									.toXMax()
									.movex(hingeDiameter+movingPartClearence)
									.movez(-linkYDimention/2)
									.transformed(hinge)
			def innerBarrel = hingeBarrel
											.toXMax()
											.movex(-centerlineToOuterSurfaceNegativeZ)
			def hingeBrace = innerBarrel
								.transformed(hinge)
								.union(servoCube).union(rightServoCube)
								.hull()
			
			def hingeBarrelMount = hingeBarrel
									.union(innerBarrel)
									.transformed(hinge)
									.union(rightServoCube)
									.hull()
									.union(hingeBrace)
									.difference(hingeSlotCutter)
			def movingCupHingeLug = tipCupCircle.roty(90)
									.movez(-linkYDimention/2)
									.transformed(hinge)
			def knotches = new Cube(centerlineToOuterSurfacePositiveZ-centerlineToOuterSurfaceNegativeZ+linkThickness*3,5,3).toCSG()
							.toXMin()
							.toZMax()
							.movex(centerlineToOuterSurfaceNegativeZ-linkThickness)
							.movey(20)
							.transformed(hinge)
			def movingHingeBarrel =  new Cylinder(hingeDiameter/2,linkThickness).toCSG()
											.movez(-linkYDimention/2-linkThickness/2)
			def movingPart = movingHingeBarrel
								.union(movingHingeBarrel
										.movex(-centerlineToOuterSurfacePositiveZ)
										)
								.hull()
								.transformed(hinge)
								.union(movingCupHingeLug.union(tipCupCircle).hull())
								.difference(hingeLinkHole.transformed(hinge))
			def gripperMovingCup = tipCupCircle.union(pincherCup).hull()
								.union(movingPart)
								.difference(objectToGrab)
								.difference(knotches)
								.difference(vitamins)
			def FullBracket =CSG.unionAll([servoBracket,supportBracket,linkToCup,pincherBracket,hingeBarrelMount])
									.difference(objectToGrab)
									.difference(vitamins)
									.difference(ActuatorBracket)
									.difference(motorToCut)
									.difference(knotches)
									
			FullBracket.setColor(javafx.scene.paint.Color.LIGHTBLUE)
			gripperMovingCup.setColor(javafx.scene.paint.Color.LIGHTPINK)
			ActuatorBracket.setColor(javafx.scene.paint.Color.DARKCYAN)
			objectToGrab.setColor(javafx.scene.paint.Color.RED)
			ActuatorBracket.setManipulator(manipulator)
			FullBracket.setManipulator(manipulator)
			gripperMovingCup.setManipulator(manipulator)
			objectToGrab.setManipulator(manipulator)
			
			FullBracket.setName("LastLinkMainBracket")
			ActuatorBracket.setName("LastLinkActuatorBracket")
			gripperMovingCup.setName("Gripper")
			objectToGrab.setName("GamePiece")
			objectToGrab.setManufacturing ({ mfg ->
				return mfg.roty(-90).toZMin()				
			})
			gripperMovingCup.setManufacturing ({ mfg ->
				return mfg.rotx(180).toZMin()				
			})
			allCad.addAll(FullBracket,ActuatorBracket,gripperMovingCup,objectToGrab)
		}
		
		if(linkIndex==0) {
			def z = dh.getD()-10-movingPartClearence/2
			def supportBeam= new RoundedCube(linkYDimention+linkThickness*2.5,40+linkThickness*2,z)
								.cornerRadius(cornerRad)
								.toCSG()
								.toZMax()
			
			def	baseOfArm = Parabola.coneByHeight(baseCorRad, 35)
						.rotx(90)
						.toZMin()
						.movez(movingPartClearence)
						
			baseOfArm=baseOfArm
						.difference(
							baseOfArm
							.getBoundingBox()
							.movez(z)
							)
						.union(supportBeam.movez(z+movingPartClearence))
						.transformed( TransformFactory.nrToCSG(locationOfBearing))
						.difference(vitamins)
			baseOfArm.setColor(javafx.scene.paint.Color.WHITE)
			baseOfArm.setManipulator(manipulator)
			baseOfArm.setName("BaseCone")
			baseOfArm.setManufacturing ({ mfg ->
				return mfg.rotx(-90).toZMin()				
			})
			allCad.add(baseOfArm)
		}
		//				CSG sparD = new Cube(gears.thickness,d.getDH_D(linkIndex),gears.thickness).toCSG()
		//						.toYMin()
		//						.toZMin()
		//				sparD.setManipulator(manipulator)
		//				allCad.add(sparD)
		d.addConnectionEventListener(new IDeviceConnectionEventListener (){

					/**
	 * Called on the event of a connection object disconnect.
	 *
	 * @param source the source
	 */
					public void onDisconnect(BowlerAbstractDevice source) {
							allCad.clear()
					}
					public void onConnect(BowlerAbstractDevice source) {}
				})
		for(def c:vitamins) {
			c.setManufacturing ({ mfg ->
			return null;
		})
		}
		allCad.addAll(vitamins)
		vitamins.clear()
		return allCad;
	}
	
	@Override
	public ArrayList<CSG> generateBody(MobileBase b ) {

		def vitaminLocations = new HashMap<TransformNR,ArrayList<String>>()
		ArrayList<CSG> allCad=new ArrayList<>();
		double baseGrid = grid;
		double baseBoltThickness=15;
		double baseCoreheight = 1;
		
		

		for(DHParameterKinematics d:b.getAllDHChains()) {
			// Hardware to engineering units configuration
			LinkConfiguration conf = d.getLinkConfiguration(0);

			b.addConnectionEventListener(new IDeviceConnectionEventListener (){

						/**
		 * Called on the event of a connection object disconnect.
		 *
		 * @param source the source
		 */
						public void onDisconnect(BowlerAbstractDevice source) {
							//gears.clear()
							allCad.clear()
						}
						public void onConnect(BowlerAbstractDevice source) {}
					})

			CSG motorModel=   Vitamins.get(conf.getElectroMechanicalType(),conf.getElectroMechanicalSize())
			double zOffset = motorModel.getMaxZ()
			TransformNR locationOfMotorMount = d.getRobotToFiducialTransform().copy()
			TransformNR locationOfBearing = locationOfMotorMount.copy()
			//move for gearing
			
			// move the motor down to allign with the shaft
			if(locationOfBearing.getZ()>baseCoreheight)
				baseCoreheight=locationOfBearing.getZ()
			locationOfMotorMount.translateZ(-zOffset)
			TransformNR pinionRoot = locationOfMotorMount.copy()
			def extractionLocationOfMotor =locationOfMotorMount.copy().translateZ(-20)

			vitaminLocations.put(locationOfBearing.copy().translateZ(-1), [
				"ballBearing",
				thrustBearingSize
			])
			vitaminLocations.put(locationOfMotorMount, [
				conf.getElectroMechanicalType(),
				conf.getElectroMechanicalSize()
			])
			vitaminLocations.put(extractionLocationOfMotor, [
				conf.getElectroMechanicalType(),
				conf.getElectroMechanicalSize()
			])
//			vitaminLocations.put(pinionRoot, [
//				conf.getShaftType(),
//				conf.getShaftSize()
//			])
		}
		def insert=["heatedThreadedInsert", "M5"]
		def insertMeasurments= Vitamins.getConfiguration(insert[0],
				insert[1])
		def mountLoacions = [
			new TransformNR(baseGrid,0,0,new RotationNR(180,0,0)),
			new TransformNR(-baseGrid,baseGrid,0,new RotationNR(180,0,0)),
			new TransformNR(-baseGrid,-baseGrid,0,new RotationNR(180,0,0))
		]

		mountLoacions.forEach{
			vitaminLocations.put(it,
					["capScrew", boltsize])
			vitaminLocations.put(it.copy().translateZ(insertMeasurments.installLength),
					insert)

		}

		double totalMass = 0;
		TransformNR centerOfMassFromCentroid=new TransformNR();
		def vitamins=[]
		for(TransformNR tr: vitaminLocations.keySet()) {
			def vitaminType = vitaminLocations.get(tr)[0]
			def vitaminSize = vitaminLocations.get(tr)[1]

			HashMap<String, Object>  measurments = Vitamins.getConfiguration( vitaminType,vitaminSize)

			CSG vitaminCad=   Vitamins.get(vitaminType,vitaminSize)
			Transform move = TransformFactory.nrToCSG(tr)
			CSG part = vitaminCad.transformed(move)
			part.setManipulator(b.getRootListener())
			vitamins.add(part)

			def massCentroidYValue = measurments.massCentroidY
			def massCentroidXValue = measurments.massCentroidX
			def massCentroidZValue = measurments.massCentroidZ
			def massKgValue = measurments.massKg
			//println "Base Vitamin "+vitaminType+" "+vitaminSize
			try {
				TransformNR COMCentroid = tr.times(
						new TransformNR(massCentroidXValue,massCentroidYValue,massCentroidZValue,new RotationNR())
						)
				totalMass+=massKgValue
			}catch(Exception ex) {
				BowlerStudio.printStackTrace(ex)
			}

			//do com calculation here for centerOfMassFromCentroid and totalMass
		}
		//Do additional CAD and add to the running CoM
		def thrustMeasurments= Vitamins.getConfiguration("ballBearing",
				thrustBearingSize)
		def baseCorRad = thrustMeasurments.outerDiameter/2+5
		CSG baseCore = new Cylinder(baseCorRad,baseCorRad,baseCoreheight,36).toCSG()
		CSG baseCoreshort = new Cylinder(baseCorRad,baseCorRad,baseCoreheight*3.0/4.0,36).toCSG()
		CSG mountLug = new Cylinder(15,15,baseBoltThickness,36).toCSG().toZMax()
		CSG mountCap = Parabola.coneByHeight(15, 8)
				.rotx(-90)
				.toZMax()
				.movez(-baseBoltThickness)
		CSG mountUnit= mountLug.union(mountCap)
		def coreParts=[baseCore]
		def boltHolePattern = []
		def boltHoleKeepawayPattern = []
		def bolt = new Cylinder(2.6,20).toCSG()
					.movez(-10)
		def boltkeepaway = new Cylinder(5,20).toCSG()
					.movez(-10)
		mountLoacions.forEach{
			
			def place =com.neuronrobotics.bowlerstudio.physics.TransformFactory.nrToCSG(it)
			boltHolePattern.add(bolt.transformed(place))
			boltHoleKeepawayPattern.add(boltkeepaway.transformed(place))
			coreParts.add(
					CSG.hullAll(mountLug
					.transformed(place)
					,baseCoreshort)
					)
			coreParts.add(mountCap
					.transformed(place)
					)
		}
		
		def locationOfCalibration = new TransformNR(0,-50,15, new RotationNR())
		DHParameterKinematics dev = b.getAllDHChains().get(0)
		//dev.setDesiredTaskSpaceTransform(locationOfCalibration, 0);
		def jointSpaceVect = dev.inverseKinematics(dev.inverseOffset(locationOfCalibration));
		println "\n\nCalibration Values "+jointSpaceVect+"\n\n"
		
		def calibrationFrame = TransformFactory.nrToCSG(locationOfCalibration)
								.movex(centerlineToOuterSurfaceNegativeZ)
		def calibrationFramemountUnit=mountUnit
										.rotx(180)
										.toYMin()
										.transformed(calibrationFrame)
										.toZMin()
										
		// assemble the base
		def calibrationTipKeepaway =new RoundedCylinder(linkYDimention/2,centerlineToOuterSurfacePositiveZ-centerlineToOuterSurfaceNegativeZ)
											.cornerRadius(cornerRad)
											.toCSG()
											.roty(-90)
									.transformed(calibrationFrame)
		coreParts.add(calibrationTipKeepaway)			
		def cordCutter = new Cube(10,40,30).toCSG()
							.toYMin()
							.toZMax()
							.movez(baseCoreheight-37)	
		
		def points = [	new Vector3d(10,0,0),
					new Vector3d(0, 0, 5),
					new Vector3d(0, -3, 0),
					new Vector3d(0, 3, 0)
		]
		CSG pointer = HullUtil.hull(points)
					
		def Base = CSG.unionAll(coreParts)
				.union(calibrationFramemountUnit)
				.union(calibrationFramemountUnit.mirrory())
				//.difference(vitamin_roundMotor_WPI_gb37y3530bracketOneKeepawayDistanceen)
				.difference(vitamins)
				.difference(calibrationTipKeepaway)
				.difference(cordCutter)
		Base = Base.union(pointer.movex(Base.getMaxX()-2))
						.union(pointer.rotz(90).movey(-baseCorRad+2))
		Base.setColor(javafx.scene.paint.Color.PINK)
		// add it to the return list
		Base.setManipulator(b.getRootListener())
		for(def c:vitamins) {
			c.setManufacturing ({ mfg ->
			return null;
		})
		}
		double extra = Math.abs(Base.getMinX())
		def paper = new Cube(8.5*25.4,11.0*25.4,5).toCSG()
						.toZMax()
						.toXMin()
						.movez(1)
						.movex(-extra)
						.difference(boltHolePattern)
		def board = new Cube(8.5*25.4,11.0*25.4,5).toCSG()
						.toZMax()
						.toXMin()
						.movex(-extra)
						.difference(boltHolePattern)
		board.setColor(javafx.scene.paint.Color.SANDYBROWN)
		def cardboard = new Cube(8.5*25.4,11.0*25.4,5).toCSG()
		.toZMax()
		.toXMin()
		.movex(-extra)
		.movez(-5)
		.difference(boltHoleKeepawayPattern)
		//.difference(vitamins)
		cardboard.setColor(javafx.scene.paint.Color.SADDLEBROWN)
		
		cardboard.addExportFormat("svg")
		board.addExportFormat("svg")
		paper.addExportFormat("svg")
		paper.setManufacturing ({ mfg ->
			return mfg.toZMin()
		})
		board.setManufacturing ({ mfg ->
			return mfg.toZMin()
		})
		cardboard.setManufacturing ({ mfg ->
			return mfg.toZMin()
		})
		paper.setColor(javafx.scene.paint.Color.WHITE)
		allCad.addAll(Base,paper,board,cardboard)
		Base.addExportFormat("stl")
		Base.addExportFormat("svg")
		Base.setName("BaseMount")
		b.setMassKg(totalMass)
		b.setCenterOfMassFromCentroid(centerOfMassFromCentroid)
		
		allCad.addAll(vitamins)
		return allCad;
	}
};

