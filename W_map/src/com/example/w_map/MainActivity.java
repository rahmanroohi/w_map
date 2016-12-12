package com.example.w_map;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.CircularRedirectException;

import com.nutiteq.MapView;
import com.nutiteq.components.Bounds;
import com.nutiteq.components.Color;
import com.nutiteq.components.Components;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.components.Options;
import com.nutiteq.components.Range;
import com.nutiteq.datasources.raster.MBOnlineRasterDataSource;
import com.nutiteq.datasources.raster.MBTilesRasterDataSource;
import com.nutiteq.datasources.vector.OGRVectorDataSource;
import com.nutiteq.datasources.vector.SpatialiteDataSource;
import com.nutiteq.db.SpatialLiteDbHelper;
import com.nutiteq.db.SpatialLiteDbHelper.DbLayer;
import com.nutiteq.layers.raster.UTFGridRasterLayer;
import com.nutiteq.layers.raster.deprecated.TMSMapLayer;
import com.nutiteq.layers.vector.DriveTimeRegionLayer;
import com.nutiteq.projections.EPSG32635;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.projections.EPSG4326;
import com.nutiteq.projections.Projection;
import com.nutiteq.rasterdatasources.HTTPRasterDataSource;
import com.nutiteq.rasterdatasources.RasterDataSource;
import com.nutiteq.rasterlayers.RasterLayer;
import com.nutiteq.renderprojections.RenderProjection;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.ui.Label;
import com.nutiteq.utils.UnscaledBitmapLoader;
import com.nutiteq.vectorlayers.GeometryLayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.audiofx.EnvironmentalReverb;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ext.R;
import android.view.ext.SatelliteMenu;
import android.view.ext.SatelliteMenuItem;
import android.view.ext.SatelliteMenu.SateliteClickedListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ZoomControls;
import jsqlite.Database;
import jsqlite.Exception;
import jsqlite.TableResult;

public class MainActivity extends Activity {

	private MapView mapView;// map

	// for gps(current postion) on the map
	private LocationListener locationListener;
	private GeometryLayer locationLayer;
	private Timer locationTimer;
	MyLocationCircle locationCircle;
	Location latLong = null;
	GeometryLayer spatialiteLayer;

	// for style object maps
	StyleSet<PointStyle> pointStyleSet;
	StyleSet<LineStyle> lineStyleSet;
	StyleSet<PolygonStyle> polygonStyleSet;
	LabelStyle labelStyle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(com.example.w_map.R.layout.activity_main);

		mapView = (MapView) findViewById(com.example.w_map.R.id.mapView);

		/**
		 * //for load arc menu and act with click child arcmenu
		 */
		menu();

		// Optional, but very useful: restore map state during device rotation,
		// it is saved in onRetainNonConfigurationInstance() below
		Components retainObject = (Components) getLastNonConfigurationInstance();
		if (retainObject != null) {
			// just restore configuration and update listener, skip other
			// initializations
			mapView.setComponents(retainObject);

			return;
		} else {
			// 2. create and set MapView components - mandatory
			mapView.setComponents(new Components());

		}

		sky();// for load sjy on the map
		zoom(true, 13, 18);// for limit area zoom
		createStyleSets();// for apply style from layer on the map
		limitMap();// for limit show area
		mapView.setFocusPoint(new EPSG3857().fromWgs84(59.600143, 36.273423));// for
																				// zom
																				// special
																				// point
		MBTilesRasterDataSource();// for load map base

		/**
		 * create layer for load
		 */

		locationLayer = new GeometryLayer(mapView.getLayers().getBaseProjection());
		mapView.getComponents().layers.addLayer(locationLayer);

		/**
		 * for zoom curent location 
		 */
		ImageButton myLocationButton = (ImageButton) findViewById(com.example.w_map.R.id.my_gps_location);
		myLocationButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (latLong != null && latLong.getLatitude() > 0)
					mapView.setFocusPoint(mapView.getLayers().getBaseProjection().fromWgs84(latLong.getLongitude(),
							latLong.getLatitude()));
			}
		});
	}

	private void createStyleSets() {
		// set styles for all 3 object types: point, line and polygon
		int minZoom = 5;
		int color = Color.BLUE;

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		float dpi = metrics.density;

		pointStyleSet = new StyleSet<PointStyle>();
		// Bitmap pointMarker =
		// UnscaledBitmapLoader.decodeResource(getResources(),
		// com.example.w_map.R.drawable.);
		PointStyle pointStyle = PointStyle.builder().setColor(color).setSize(0.1f)
				.build();/*
							 * builder().setBitmap(pointMarker).setSize(0.35f).
							 * setPickingSize(0.35f)
							 */

		pointStyleSet.setZoomStyle(minZoom, pointStyle);

		lineStyleSet = new StyleSet<LineStyle>();
		LineStyle lineStyle = LineStyle.builder().setWidth(0.05f).setColor(color).build();
		lineStyleSet.setZoomStyle(minZoom, lineStyle);

		PolygonStyle polygonStyle = PolygonStyle.builder().setColor(color & 0x80FFFFFF).setLineStyle(lineStyle).build();
		polygonStyleSet = new StyleSet<PolygonStyle>(polygonStyle);

		labelStyle = LabelStyle.builder().setEdgePadding((int) (12 * dpi)).setLinePadding((int) (6 * dpi))
				.setTitleFont(Typeface.create("Arial", Typeface.BOLD), (int) (16 * dpi))
				.setDescriptionFont(Typeface.create("Arial", Typeface.NORMAL), (int) (13 * dpi)).build();
	}

	private void addOgrLayer(Projection proj, String dbPath, String table, int color) {
		OGRVectorDataSource dataSource;
		try {
			dataSource = new OGRVectorDataSource(proj, dbPath, table) {

				@Override
				protected StyleSet<PointStyle> createPointStyleSet(Map<String, String> userData, int zoom) {
					return pointStyleSet;
				}

				@Override
				protected StyleSet<LineStyle> createLineStyleSet(Map<String, String> userData, int zoom) {
					return lineStyleSet;
				}

				@Override
				protected StyleSet<PolygonStyle> createPolygonStyleSet(Map<String, String> userData, int zoom) {
					return polygonStyleSet;
				}

				@Override
				protected Label createLabel(Map<String, String> arg0) {
					// TODO Auto-generated method stub
					// return null;

					StringBuffer labelTxt = new StringBuffer();
					for (Map.Entry<String, String> entry : arg0.entrySet()) {
						labelTxt.append(entry.getKey() + ":" + entry.getValue() + "\n");
					}
					return (new DefaultLabel("Data:", labelTxt.toString(), labelStyle));

				}

			};
		} catch (IOException e) {

			Toast.makeText(this, "ERROR " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			return;
		}

		dataSource.setMaxElements(1000);

		GeometryLayer ogrLayer = new GeometryLayer(dataSource);
		mapView.getLayers().addLayer(ogrLayer);

		Envelope extent = ogrLayer.getDataExtent();
		mapView.setBoundingBox(new Bounds(extent.minX, extent.maxY, extent.maxX, extent.minY), false);
	}

	void menu() {
		SatelliteMenu menu = (SatelliteMenu) findViewById(com.example.w_map.R.id.menu);
		List<SatelliteMenuItem> items = new ArrayList<SatelliteMenuItem>();

		items.add(new SatelliteMenuItem(4, R.drawable.ic_launcher));
		items.add(new SatelliteMenuItem(3, R.drawable.ic_launcher));
		items.add(new SatelliteMenuItem(2, R.drawable.ic_launcher));
		items.add(new SatelliteMenuItem(1, R.drawable.ic_launcher));
		//
		menu.addItems(items);

		menu.setOnItemClickedListener(new SateliteClickedListener() {

			public void eventOccured(int id) {

				switch (id) {
				case 1:
					if (spatialiteLayer != null) {
						mapView.getLayers().removeLayer(spatialiteLayer);
						spatialiteLayer = null;
					} else
						addOgrLayerSpatit(new EPSG3857(), Environment.getExternalStorageDirectory() + File.separator
								+ "maps" + File.separator + "manhole1.sqlite", "manhole1", Color.BLUE);
					break;
				case 2:

					break;
				case 3:

					break;
				case 4:

					break;

				default:
					break;
				}
			}
		});

	}

	void online_layer() {
		RasterDataSource rasterDataSource = new HTTPRasterDataSource(new EPSG4326(), 0, 19,
				"http://kaart.maakaart.ee/osm/tms/1.0.0/osm_noname_st_EPSG4326/{zoom}/{x}/{yflipped}.png");

		RasterLayer mapLayer = new RasterLayer(rasterDataSource, 12);

		mapView.getLayers().setBaseLayer(mapLayer);

	}

	void zoom(boolean state, int zommin, int zommax) {
		if (state) {
			// for zoom
			ZoomControls zoomControls = (ZoomControls) findViewById(com.example.w_map.R.id.zoomcontrols);
			zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
				public void onClick(final View v) {

					mapView.zoomIn();
				}
			});
			zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
				public void onClick(final View v) {

					mapView.zoomOut();
				}
			});

		}

		mapView.getConstraints().setZoomRange(new Range(zommin, zommax));
		mapView.setZoom(13);
	}

	void sky() {
		mapView.getOptions().setSkyDrawMode(Options.DRAW_BITMAP);
		mapView.getOptions().setSkyOffset(4.86f);
		mapView.getOptions().setSkyBitmap(
				UnscaledBitmapLoader.decodeResource(getResources(), com.example.w_map.R.drawable.sky_small));

	}

	/**
	 * for shp file load
	 */

	// tile map layer
	void MBTilesRasterDataSource() {

		MBTilesRasterDataSource dataSource = null;
		try {
			dataSource = new MBTilesRasterDataSource(new EPSG3857(), 12, 16, Environment.getExternalStorageDirectory()
					+ File.separator + "maps" + File.separator + "mashad2.mbtiles", false, this);
			RasterLayer mapLayer = new RasterLayer(dataSource, 12);
			mapView.getLayers().setBaseLayer(mapLayer);

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	void limitMap() {
		MapPos topLeft = new EPSG3857().fromWgs84(59.402981, 36.386035);
		MapPos bottomRight = new EPSG3857().fromWgs84(59.739437, 36.216978);
		mapView.getConstraints().setMapBounds(new Bounds(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y));
	}

	public static void db_R_W(String strPath)
	{
		Database db = new jsqlite.Database();
		try {
			db.open(strPath, jsqlite.Constants.SQLITE_OPEN_READWRITE
                    | jsqlite.Constants.SQLITE_OPEN_CREATE);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String query="Insert into manhole1 (Geometry) values(GeomFromText('POINT(6624032.335221 4348910.155217)' , 3857));";
        try{
             
            db.exec(query, null);
            db.close();
        } catch (Exception e) {
            
            e.printStackTrace();
        }
        
       /* try {
			TableResult db11 = db.get_table("select * from manhole1");
			int rows=db11.maxrows;

			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			db.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}
	private void addOgrLayerSpatit(Projection proj, String dbPath, String table, int color) {

		// minimum zoom to show data
		int minZoom = 5;
		db_R_W(dbPath);
		SpatialLiteDbHelper spatialLite;
		// open database connection, query metadata
		try {
			spatialLite = new SpatialLiteDbHelper(dbPath);
		} catch (IOException e) {

			Toast.makeText(this, "ERROR " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			return;
		}

		Map<String, DbLayer> dbMetaData = spatialLite.qrySpatialLayerMetadata();

		ArrayList<String> tables = new ArrayList<String>();

		for (String layerKey : dbMetaData.keySet()) {
			SpatialLiteDbHelper.DbLayer layer = dbMetaData.get(layerKey);
			Log.d("",
					"layer: " + layer.table + " " + layer.type + " geom:" + layer.geomColumn + " SRID: " + layer.srid);
			tables.add(layerKey);
		}

		// String[] tableKey = tables.get(0).toString().sp
		SpatialiteDataSource dataSource = null;
		try {
			dataSource = new SpatialiteDataSource(proj, dbPath, "manhole1", "geometry", null, null) {
				@Override
				protected Label createLabel(Map<String, String> userData) {
					// create popup label for object based on attribute values -
					// here just add all table fields
					StringBuffer labelTxt = new StringBuffer();
					for (Map.Entry<String, String> entry : userData.entrySet()) {
						labelTxt.append(entry.getKey() + ": " + entry.getValue() + "\n");
					}
					return new DefaultLabel("Data:", labelTxt.toString(), labelStyle);

				}

				// to make styles depending on object attributes use userData
				// (see createLabel)
				@Override
				protected StyleSet<PointStyle> createPointStyleSet(Map<String, String> userData, int zoom) {
					return pointStyleSet;
				}

				@Override
				protected StyleSet<LineStyle> createLineStyleSet(Map<String, String> userData, int zoom) {
					return lineStyleSet;
				}

				@Override
				protected StyleSet<PolygonStyle> createPolygonStyleSet(Map<String, String> userData, int zoom) {
					return polygonStyleSet;
				}
			};
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {

		}

		// do not load more than 1000 objects - avoid memory overflow. you can
		// load more points, less polygons
		dataSource.setMaxElements(300);

		// define 2 pixel accuracy (based on screen width) for automatic
		// polygon/line simplification
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		dataSource.setAutoSimplify(2, metrics.widthPixels);

		// create and add layer to map
		spatialiteLayer = new GeometryLayer(dataSource);
		mapView.getLayers().addLayer(spatialiteLayer);

		// finally zoom mapView to map data extents
		Envelope extent = spatialiteLayer.getDataExtent();
		mapView.setBoundingBox(new Bounds(extent.minX, extent.maxY, extent.maxX, extent.minY), false);
	}

	@Override
	protected void onStart() {
		super.onStart();

		// 4. Start the map - mandatory.
		mapView.startMapping();

		// Create layer for location circle
		locationLayer = new GeometryLayer(mapView.getLayers().getBaseProjection());
		mapView.getComponents().layers.addLayer(locationLayer);

		// gps start
		gps_start();
	}

	// add GPS My Location functionality
	void gps_start() {

		// add GPS My Location functionality
		final MyLocationCircle locationCircle = new MyLocationCircle(locationLayer);
		initGps(locationCircle);

		// Run animation
		locationTimer = new Timer();
		locationTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				locationCircle.update(mapView.getZoom());
			}
		}, 0, 50);
	}

	@Override
	protected void onStop() {
		// Stop animation
		locationTimer.cancel();

		// Remove created layer
		mapView.getComponents().layers.removeLayer(locationLayer);

		// remove GPS support, otherwise we will leak memory
		deinitGps();

		// Note: it is recommended to move startMapping() call to onStart method
		// and implement onStop method (call MapView.stopMapping() from onStop).
		mapView.stopMapping();

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	protected void initGps(final MyLocationCircle locationCircle) {
		final Projection proj = mapView.getLayers().getBaseLayer().getProjection();

		locationListener = new LocationListener() {
			@Override
			public void onLocationChanged(Location location) {
				locationCircle.setLocation(proj, location);
				locationCircle.setVisible(true);
				latLong = location;
				// recenter automatically to GPS point
				// TODO in real app it can be annoying this way, add extra
				// control that it is done only once

			}

			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {
				// Log.debug("GPS onStatusChanged "+provider+" to "+status);
			}

			@Override
			public void onProviderEnabled(String provider) {
				// Log.debug("GPS onProviderEnabled");
			}

			@Override
			public void onProviderDisabled(String provider) {
				// Log.debug("GPS onProviderDisabled");
			}
		};

		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 100, locationListener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);

	}

	protected void deinitGps() {
		// remove listeners from location manager - otherwise we will leak
		// memory
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		locationManager.removeUpdates(locationListener);
	}

}
