package org.gicentre.utils.network;

import java.util.HashMap;
import java.util.Map;

import org.gicentre.utils.move.ZoomPan;

import processing.core.PApplet;
import processing.core.PConstants;
import traer.animation.Smoother3D;
import traer.physics.Attraction;
import traer.physics.Particle;
import traer.physics.ParticleSystem;
import traer.physics.Spring;
import traer.physics.Vector3D;

// *****************************************************************************************
/** Allows particles to be viewed and animated. Suitable for spring embedded / force directed
 *  layouts for arranging networks and other collections of interacting objects. Note this
 *  class relies on the <a href="http://www.cs.princeton.edu/~traer/physics/">traer physics library</a>
 *  for particle management and the <a href="http://www.cs.princeton.edu/~traer/animation">traer
 *  animation library</a> for smooth camera movement. 
 *  @author Jo Wood, giCentre, City University London.
 *  @version 3.1, 23rd November, 2010. 
 */ 
// *****************************************************************************************

/* This file is part of giCentre utilities library. gicentre.utils is free software: you can 
 * redistribute it and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * gicentre.utils is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this
 * source code (see COPYING.LESSER included with this source code). If not, see 
 * http://www.gnu.org/licenses/.
 */

public class ParticleViewer
{
    // ----------------------------- Object variables ------------------------------
   
	private PApplet parent;			  // Processing applet that controls the drawing.
	private ParticleSystem physics;   // The environment for particle modelling.
	private Smoother3D centroid;      // For smooth camera centring.
	private int width,height;		  // Dimensions of the drawable area.
	private boolean isPaused;		  // Controls whether or not the particles animate.
	private HashMap<Node, Particle> nodes;
	private HashMap<Edge, Spring> edges;
	private ZoomPan zoomer;           // For interactive zooming and panning.
	private Node selectedNode;		  // Optionally selected node for query or interaction.
	
	private static final float EDGE_STRENGTH   = 1;
	private static final float SPRING_STRENGTH = 0.5f;
	private static final float DAMPING         = 0.1f;
          
    // ------------------------------- Constructors --------------------------------
    
    /** Initialises the particle viewer.
     *  @param parent Parent sketch in which this viewer is to be drawn.
     */
    public ParticleViewer(PApplet parent, int width, int height)
    {
        this.parent = parent;
        zoomer = new ZoomPan(parent);
        zoomer.setMouseMask(PConstants.SHIFT);
        centroid = new Smoother3D(0.9f); 
        physics  = new ParticleSystem(0, 0.75f);    // No gravity with .75 drag.
        nodes = new HashMap<Node, Particle>();
        edges = new HashMap<Edge, Spring>();
        this.width = width;
        this.height = height;
        isPaused = false;
        selectedNode = null;
    }
    
    // ---------------------------------- Methods ----------------------------------
        
    /** Updates the particle view. This should be called on each draw cycle in order
     *  to update the positions of all nodes and edges in the viewer. If you need to update
     *  the positions of particles without drawing it (e.g. to speed up movement, call 
     *  updateParticles() instead.
     */
    public void draw()
    {
    	parent.pushMatrix();
    	zoomer.transform();
        updateCentroid();
        centroid.tick();
        
        parent.translate(width/2, height/2);
        parent.scale(centroid.z());
        parent.translate(-centroid.x(), -centroid.y());
        
        if (!isPaused)
        {
           updateParticles();
        }
        
        // Ensure that any selected element is positioned at the mouse location.
        if (selectedNode != null)
        {
        	Particle p = nodes.get(selectedNode);
            p.makeFixed();
            float mX = (zoomer.getMouseCoord().x -(width/2))/centroid.z() + centroid.x();
            float mY = (zoomer.getMouseCoord().y -(height/2))/centroid.z() + centroid.y();
            p.position().set(mX,mY,0); 
        }
        
        // Draw edges if we have positive stroke weight.
        if (parent.g.strokeWeight > 0)
        {
        	parent.stroke(0,180);
        	parent.noFill();

        	for (Map.Entry<Edge,Spring> row: edges.entrySet() )
        	{
        		Edge edge = row.getKey();
        		Spring spring = row.getValue();
        		Vector3D p1 = spring.getOneEnd().position();
        		Vector3D p2 = spring.getTheOtherEnd().position();
        		edge.draw(parent, p1.x(),p1.y(),p2.x(),p2.y());
        	}
        }
        
        
        // Draw nodes.
        parent.noStroke();
        parent.fill(120,50,50,180);
        
        for (Map.Entry<Node,Particle> row: nodes.entrySet() )
        {
        	Node node = row.getKey();
        	Vector3D p = row.getValue().position();
        	node.draw(parent, p.x(),p.y());
        }

        parent.popMatrix();
    }
    
    /** Updates the positions of nodes and edges in the viewer. This method does not normally need
     *  to be called as update happens every time draw() is called. Calling this method can be useful
     *  if you wish to speed up the movement of nodes and edges by updating their position more than
     *  once every draw cycle.
     */
    public void updateParticles()
    {
    	 physics.tick(0.3f);         // Advance time in the physics engine.
    }
     
    /** Sets the drag on all particles in the system. By default drag is set to 0.75 which 
     *  is enough to allow particles to move smoothly. 
     *  @param drag Drag effect (larger numbers slow down movement).
     */
    public void setDrag(float drag)
    {
    	physics.setDrag(drag);
    }
    
    /** Creates a attractive or repulsive force between the two given nodes. If the two nodes
     *  already have a force between them, it will be replaced by this one.
     * @param node1 First of the two nodes to have a force between them. 
     * @param node2 Second of the two nodes to have a force between them.
     * @param force Force to create between the two nodes. If positive, the nodes will attract
     *              each other, if negative they will repulse. The larger the magnitude the stronger the force.
     * @return True if the viewer contains the two nodes and a force between them has been created.
     */
    public boolean addForce(Node node1, Node node2, float force)
    {
    	Particle p1 = nodes.get(node1);
    	if (p1 == null)
    	{
    		return false;
    	}
    	Particle p2 = nodes.get(node2);
    	if (p2 == null)
    	{
    		return false;
    	}
    	
    	// We may have to remove existing force if it exists between these two nodes.
    	for (int i=0; i<physics.numberOfAttractions(); i++)
    	{
    		Attraction a = physics.getAttraction(i);
    		if (((a.getOneEnd() == p1) && (a.getTheOtherEnd() == p2)) ||
    			((a.getOneEnd() == p2) && (a.getTheOtherEnd() == p1)))
    		{
    			physics.removeAttraction(a);
    			break;
    		}
    	}
    	
    	// Add the new force.
    	physics.makeAttraction(p1,p2, force, 0.1f);
    	return false;
    }
    
    /** Creates a spring between the two given nodes. If the two nodes not directly connected by an
     *  edge already have a spring between them, it will be replaced by this one. The strength of the
     *  spring will be less than that of connected edges.
     * @param node1 First of the two nodes to have a spring between them. 
     * @param node2 Second of the two nodes to have a spring between them.
     * @param length The length of this spring (natural rest distance at which the two nodes would sit).
     * @return True if the viewer contains the two nodes and a spring between them has been created.
     */
    public boolean addSpring(Node node1, Node node2, float length)
    {
    	Particle p1 = nodes.get(node1);
    	if (p1 == null)
    	{
    		return false;
    	}
    	Particle p2 = nodes.get(node2);
    	if (p2 == null)
    	{
    		return false;
    	}
    	
    	// We may have to remove existing spring if it exists between these two nodes.
    	for (int i=0; i<physics.numberOfSprings(); i++)
    	{
    		Spring spring = physics.getSpring(i);
    		if ((((spring.getOneEnd() == p1) && (spring.getTheOtherEnd() == p2)) ||
    			((spring.getOneEnd() == p2) && (spring.getTheOtherEnd() == p1))) &&
    			(spring.strength() != EDGE_STRENGTH))
    		{
    			physics.removeSpring(spring);
    			break;
    		}
    	}
    	
    	// Add the new force.
    	physics.makeSpring(p1,p2, SPRING_STRENGTH, DAMPING,length);
    	return false;
    }
    
    /** Adds a node to those to be displayed in the viewer.
     * @param node Node to add to the viewer.
     */
    public void addNode(Node node)
    {
    	Particle p = physics.makeParticle(1, node.getLocation().x, node.getLocation().y, 0);
    	nodes.put(node,p);
    }
    
    /** Adds the given edge to those to be displayed in the viewer. Note that the edge must connect
     *  nodes that have already been added to the viewer.
     *  @param edge Edge to add to the display.
     *  @return True if edge was added successfully. False if edge contains nodes that have not been
     *               added to the viewer.
     */
    public boolean addEdge(Edge edge)
    {
    	
    	Particle p1 = nodes.get(edge.getNode1());
    	if (p1 == null)
    	{
    		System.err.println("Warning: Node1 not found when creating edge.");
    		return false;
    	}
    	Particle p2 = nodes.get(edge.getNode2());
    	if (p2 == null)
    	{
    		System.err.println("Warning: Node2 not found when creating edge.");
    		return false;
    	}
    	
    	// Only add edge if it does not already exist in the collection
    	if (!edges.containsKey(edge))
    	{
    		float x1 = p1.position().x();
    		float y1 = p1.position().y();
    		float x2 = p2.position().x();
    		float y2 = p2.position().y();
    												   // Strength, damping, reset length
    		edges.put(edge, physics.makeSpring(p1, p2, 
    				  EDGE_STRENGTH, DAMPING, (float)Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2))));
    	}
    	return true;
    }
    
    
    /** Allows a node to be selected with the mouse.
     */
    public void selectNearestWithMouse()
    {
        if (!zoomer.isMouseCaptured())
        {
            float mX = (zoomer.getMouseCoord().x -(width/2))/centroid.z() + centroid.x();
            float mY = (zoomer.getMouseCoord().y -(height/2))/centroid.z() + centroid.y();
        	
            if (selectedNode == null)
            {
                float nearestDSq = Float.MAX_VALUE;

                for (Map.Entry<Node,Particle> row: nodes.entrySet())
                {
                	Node node = row.getKey();
                	Particle p = row.getValue();
                	
                    float px = p.position().x();
                    float py = p.position().y();
                    float dSq = (px-mX)*(px-mX) + (py-mY)*(py-mY);
                    if (dSq < nearestDSq)
                    {
                        nearestDSq = dSq;
                        selectedNode = node;
                    }
                }
            }
        }
    }

    /** Releases the mouse-selected node so that it readjusts in response to other node positions.
     */
    public void dropSelected()
    {
        if (!zoomer.isMouseCaptured())
        {            
            if (selectedNode != null)
            {
                nodes.get(selectedNode).makeFree();
                selectedNode = null;
            }
        }
    }
    
    /** Resets the zoomed view to show the entire network.
     */
    public void resetView()
    {
    	zoomer.reset();
    }
    
    // ------------------------------ Private methods ------------------------------
    
    /** Centres the particle view on the currently visible nodes.
     */
    private void updateCentroid()
    {
        float xMax = Float.NEGATIVE_INFINITY, 
        xMin = Float.POSITIVE_INFINITY, 
        yMin = Float.POSITIVE_INFINITY, 
        yMax = Float.NEGATIVE_INFINITY;

        for (int i=0; i<physics.numberOfParticles(); ++i)
        {
            Particle p = physics.getParticle(i);
            xMax = Math.max(xMax, p.position().x());
            xMin = Math.min(xMin, p.position().x());
            yMin = Math.min(yMin, p.position().y());
            yMax = Math.max(yMax, p.position().y());
        }

        float xRange = xMax-xMin;
        float yRange = yMax-yMin;
        float zScale = (float)Math.min(height/(yRange*1.2),width/(xRange*1.2));
        centroid.setTarget(xMin+0.5f*xRange, yMin+0.5f*yRange, zScale);
    }
}