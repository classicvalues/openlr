package openlr.map.simplemockdb;

import com.vividsolutions.jts.geom.*;
import openlr.map.Line;
import openlr.map.Node;
import openlr.map.GeoCoordinates;
import openlr.map.FormOfWay;
import openlr.map.FunctionalRoadClass;
import openlr.map.GeoCoordinatesImpl;
import openlr.map.InvalidMapDataException;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.cs.DefaultCartesianCS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;


import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;



public class SimpleMockedLine implements Line {

    private generated.Line xmlLine;
    private static GeometryFactory factory = JTSFactoryFinder.getGeometryFactory();
    private Node startNode;
    private Node endNode;
    private List <LineSegment> lineSegments;

    public final LineString getLineString()
    {
        Coordinate[] crds = new Coordinate[ getShapeCoordinates().size()];
        getShapeCoordinates().stream().map(shapePoint ->{
            return tranformToCartesian(shapePoint.getLongitudeDeg(),shapePoint.getLatitudeDeg());
        }).collect(Collectors.toList()).toArray();
        return factory.createLineString(crds);
    }


    public static Coordinate tranformToCartesian(double lon,double lat){
        final Coordinate to = new Coordinate();
        try {
            MathTransform convert = CRS.findMathTransform( DefaultGeographicCRS.WGS84, DefaultGeocentricCRS.CARTESIAN);
            JTS.transform(new Coordinate(lon,lat,0),to,convert);
            return to;
        } catch (FactoryException e){
            throw new SimpleMockedException(e.getMessage());

        } catch (TransformException e){
            throw new SimpleMockedException(e.getMessage());

        }
    }


    public static GeoCoordinates transformToWGS84(Coordinate crd){
        try {
            final Coordinate to = new Coordinate();
            MathTransform convert = CRS.findMathTransform(DefaultGeocentricCRS.CARTESIAN, DefaultGeographicCRS.WGS84_3D);
            JTS.transform(crd,to,convert);
            return new GeoCoordinatesImpl(to.x,to.y);
        } catch (FactoryException e){

            throw new SimpleMockedException(e.getMessage());

        } catch (TransformException e){
            throw new SimpleMockedException(e.getMessage());

        } catch (InvalidMapDataException e){
            throw new SimpleMockedException(e.getMessage());

        }
    }

    public SimpleMockedLine(generated.Line xmlLine, Node startNode, Node endNode) {
        this.xmlLine = xmlLine;
        this.startNode = startNode;
        this.endNode = endNode;
        this.lineSegments = new ArrayList<>();
        int numberOfShapePoints = xmlLine.getShapePoint().size()+2;
        Coordinate[] shapePoints = new Coordinate[numberOfShapePoints];
        shapePoints[0] = tranformToCartesian(this.startNode.getLongitudeDeg(), this.startNode.getLatitudeDeg());
        for(int index=0;index<xmlLine.getShapePoint().size();++index){
            generated.Line.ShapePoint shapePoint = xmlLine.getShapePoint().get(index);
            shapePoints[index+1] = tranformToCartesian(shapePoint.getLongitude(), shapePoint.getLatitude());
        }
        shapePoints[numberOfShapePoints-1] = tranformToCartesian(this.endNode.getLongitudeDeg(), this.endNode.getLatitudeDeg());
        for(int index=1;index < shapePoints.length; ++index){
            Coordinate end = shapePoints[index];
            Coordinate start = shapePoints[index-1];
            LineSegment segment = new LineSegment(start,end);
            lineSegments.add(segment);
        }

    }

    public Node getStartNode() {
        return this.startNode;
    }

    public List<Long> getRestrictions() {
        return xmlLine.getRestrictions().stream().map(line -> line.longValue()).collect(Collectors.toList());
    }

    public Node getEndNode() {
        return this.endNode;
    }

    public FormOfWay getFOW() {

        return FormOfWay.getFOWs().get(xmlLine.getFow());
    }

    public FunctionalRoadClass getFRC() {
        return FunctionalRoadClass.getFRCs().get(xmlLine.getFrc());
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Point2D.Double getPointAlongLine(int var1) {
        return null;
    }

    public GeoCoordinates getGeoCoordinateAlongLine(int distance) throws SimpleMockedException {

        int lengthCovered = 0;
        for(LineSegment segment : lineSegments){
            if(lengthCovered <= distance && lengthCovered+segment.getLength() >= distance){
                double segmentLengthFraction = distance - lengthCovered;
                Coordinate point = segment.pointAlong(segmentLengthFraction);
                double z = segment.p0.z + segmentLengthFraction * (segment.p1.z - segment.p0.z);
                point.setOrdinate(2,z);
                return transformToWGS84(point);
            }
            lengthCovered += segment.getLength();
        }
        throw new SimpleMockedException("length is greater than the line length");
    }

    public int getLineLength() {
        return (int) this.lineSegments.stream().mapToDouble(segment -> segment.getLength()).sum();
    }

    public long getID() {
        return xmlLine.getId().longValue();
    }

    public Iterator<Line> getPrevLines() {
        return this.startNode.getIncomingLines();
    }

    /**
     * @deprecated
     */
    @Deprecated
    public java.awt.geom.Path2D.Double getShape() {
        return null;
    }

    public Iterator<Line> getNextLines() {
        return this.endNode.getOutgoingLines();
    }

    public boolean equals(Object var1) {
        if (var1 instanceof SimpleMockedLine) {
            return this.getID() == ((SimpleMockedLine) var1).getID();
        }
        return false;
    }

    public int hashCode() {
        return this.xmlLine.hashCode();
    }

    public int distanceToPoint(double var1, double var3) {
        Geometry geometry = factory.createPoint(tranformToCartesian(var1,var3));
        return (int) Math.round(getLineString().distance(geometry));
    }

    public int measureAlongLine(double lon, double lat) {
        Geometry geometry = factory.createPoint(tranformToCartesian(lon,lat));
        return (int) Math.round(getLineString().distance(geometry));
    }

    public List<GeoCoordinates> getShapeCoordinates() {
        List<GeoCoordinates> shape = new ArrayList<>();
        for(int index=0;index < lineSegments.size();++index)
        {
            shape.add(transformToWGS84(lineSegments.get(index).p0));

            if(index==lineSegments.size()-1) {
                shape.add(transformToWGS84(lineSegments.get(index).p1));
            }
        }
        return shape;
    }

    public Map<Locale, List<String>> getNames() {
        return null;
    }
}
