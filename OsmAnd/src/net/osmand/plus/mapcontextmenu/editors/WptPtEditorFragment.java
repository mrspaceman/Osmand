package net.osmand.plus.mapcontextmenu.editors;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.View;

import net.osmand.data.LatLon;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.GPXUtilities.WptPt;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.MapMarkersHelper;
import net.osmand.plus.MapMarkersHelper.MapMarkersGroup;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.SavingTrackHelper;
import net.osmand.plus.base.FavoriteImageDrawable;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapcontextmenu.editors.WptPtEditor.OnDismissListener;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.Map;

public class WptPtEditorFragment extends PointEditorFragment {

	protected WptPtEditor editor;
	protected WptPt wpt;
	private SavingTrackHelper savingTrackHelper;
	private GpxSelectionHelper selectedGpxHelper;

	private boolean saved;
	private int color;
	private int defaultColor;
	protected boolean skipDialog;
	private Map<String, Integer> categoriesMap;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		savingTrackHelper = getMapActivity().getMyApplication().getSavingTrackHelper();
		selectedGpxHelper = getMapActivity().getMyApplication().getSelectedGpxHelper();
		assignEditor();
		defaultColor = getResources().getColor(R.color.gpx_color_point);
	}

	@Override
	protected DialogFragment createSelectCategoryDialog() {
		SelectCategoryDialogFragment selectCategoryDialogFragment = SelectCategoryDialogFragment.createInstance(getEditor().getFragmentTag());
		GPXFile gpx = editor.getGpxFile();
		if (gpx != null) {
			selectCategoryDialogFragment.setGpxFile(gpx);
			selectCategoryDialogFragment.setGpxCategories(categoriesMap);
		}
		return selectCategoryDialogFragment;
	}

	protected void assignEditor() {
		editor = getMapActivity().getContextMenu().getWptPtPointEditor();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		wpt = editor.getWptPt();
		color = wpt.getColor(0);
		categoriesMap = editor.getGpxFile().getWaypointCategoriesWithColors(false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		if (skipDialog) {
			save(true);
		}
	}

	@Override
	public void dismiss(boolean includingMenu) {
		super.dismiss(includingMenu);
		OnDismissListener listener = editor.getOnDismissListener();
		if (listener != null) {
			listener.onDismiss();
		}
		editor.setNewGpxPointProcessing(false);
		editor.setOnDismissListener(null);
	}

	@Override
	public PointEditor getEditor() {
		return editor;
	}

	@Override
	public String getToolbarTitle() {
		if (editor.isNewGpxPointProcessing()) {
			return getMapActivity().getResources().getString(R.string.save_gpx_waypoint);
		} else {
			if (editor.isNew()) {
				return getMapActivity().getResources().getString(R.string.context_menu_item_add_waypoint);
			} else {
				return getMapActivity().getResources().getString(R.string.shared_string_edit);
			}
		}
	}

	public static void showInstance(final MapActivity mapActivity) {
		WptPtEditor editor = mapActivity.getContextMenu().getWptPtPointEditor();
		WptPtEditorFragment fragment = new WptPtEditorFragment();
		mapActivity.getSupportFragmentManager().beginTransaction()
				.add(R.id.fragmentContainer, fragment, editor.getFragmentTag())
				.addToBackStack(null).commit();
	}

	public static void showInstance(final MapActivity mapActivity, boolean skipDialog) {
		WptPtEditor editor = mapActivity.getContextMenu().getWptPtPointEditor();
		WptPtEditorFragment fragment = new WptPtEditorFragment();
		fragment.skipDialog = skipDialog;
		mapActivity.getSupportFragmentManager().beginTransaction()
				.add(R.id.fragmentContainer, fragment, editor.getFragmentTag())
				.addToBackStack(null).commit();
	}

	@Override
	protected boolean wasSaved() {
		return saved;
	}

	@Override
	protected void save(final boolean needDismiss) {
		String name = Algorithms.isEmpty(getNameTextValue()) ? null : getNameTextValue();
		String category = Algorithms.isEmpty(getCategoryTextValue()) ? null : getCategoryTextValue();
		String description = Algorithms.isEmpty(getDescriptionTextValue()) ? null : getDescriptionTextValue();
		if (editor.isNew()) {
			doAddWpt(name, category, description);
		} else {
			doUpdateWpt(name, category, description);
		}
		getMapActivity().refreshMap();
		if (needDismiss) {
			dismiss(false);
		}

		MapContextMenu menu = getMapActivity().getContextMenu();

		if (menu.getLatLon() != null && menu.isActive()) {

			LatLon latLon = new LatLon(wpt.getLatitude(), wpt.getLongitude());

			if (menu.getLatLon().equals(latLon)) {
				menu.update(latLon, wpt.getPointDescription(getMapActivity()), wpt);
			}
		}

		saved = true;
	}

	private void syncGpx(GPXFile gpxFile) {
		MapMarkersHelper helper = getMyApplication().getMapMarkersHelper();
		MapMarkersGroup group = helper.getMarkersGroup(gpxFile);
		if (group != null) {
			helper.runSynchronization(group);
		}
	}

	private void doAddWpt(String name, String category, String description) {
		wpt.name = name;
		wpt.category = category;
		wpt.desc = description;
		if (color != 0) {
			wpt.setColor(color);
		} else {
			wpt.removeColor();
		}

		GPXFile gpx = editor.getGpxFile();
		if (gpx != null) {
			if (gpx.showCurrentTrack) {
				wpt = savingTrackHelper.insertPointData(wpt.getLatitude(), wpt.getLongitude(),
						System.currentTimeMillis(), description, name, category, color);
				if (!editor.isGpxSelected()) {
					selectedGpxHelper.setGpxFileToDisplay(gpx);
				}
			} else {
				addWpt(gpx, description, name, category, color);
				new SaveGpxAsyncTask(getMyApplication(), gpx, editor.isGpxSelected()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			syncGpx(gpx);
		}
	}

	protected void addWpt(GPXFile gpx, String description, String name, String category, int color) {
		wpt = gpx.addWptPt(wpt.getLatitude(), wpt.getLongitude(),
				System.currentTimeMillis(), description, name, category, color);
		syncGpx(gpx);
	}

	private void doUpdateWpt(String name, String category, String description) {
		GPXFile gpx = editor.getGpxFile();
		if (gpx != null) {
			if (gpx.showCurrentTrack) {
				savingTrackHelper.updatePointData(wpt, wpt.getLatitude(), wpt.getLongitude(),
						System.currentTimeMillis(), description, name, category, color);
				if (!editor.isGpxSelected()) {
					selectedGpxHelper.setGpxFileToDisplay(gpx);
				}
			} else {
				gpx.updateWptPt(wpt, wpt.getLatitude(), wpt.getLongitude(),
						System.currentTimeMillis(), description, name, category, color);
				new SaveGpxAsyncTask(getMyApplication(), gpx, editor.isGpxSelected()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			syncGpx(gpx);
		}
	}

	@Override
	protected void delete(final boolean needDismiss) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(getString(R.string.context_menu_item_delete_waypoint));
		builder.setNegativeButton(R.string.shared_string_no, null);
		builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

				GPXFile gpx = editor.getGpxFile();
				if (gpx != null) {
					if (gpx.showCurrentTrack) {
						savingTrackHelper.deletePointData(wpt);
					} else {
						gpx.deleteWptPt(wpt);
						new SaveGpxAsyncTask(getMyApplication(), gpx, editor.isGpxSelected()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
					syncGpx(gpx);
				}
				saved = true;

				if (needDismiss) {
					dismiss(true);
				} else {
					getMapActivity().refreshMap();
				}
			}
		});
		builder.create().show();
	}

	@Override
	public void setCategory(String name, int color) {
		if (categoriesMap != null) {
			categoriesMap.put(name, color);
		}
		this.color = color;
		super.setCategory(name, color);
	}

	@Override
	protected String getDefaultCategoryName() {
		return getString(R.string.shared_string_favorites);
	}

	@Override
	public String getHeaderCaption() {
		return getMapActivity().getResources().getString(R.string.shared_string_waypoint);
	}

	@Override
	public String getNameInitValue() {
		return wpt.name;
	}

	@Override
	public String getCategoryInitValue() {
		return Algorithms.isEmpty(wpt.category) ? "" : wpt.category;
	}

	@Override
	public String getDescriptionInitValue() {
		return wpt.desc;
	}

	@Override
	public Drawable getNameIcon() {
		return FavoriteImageDrawable.getOrCreate(getMapActivity(), getPointColor(), false);
	}

	@Override
	public Drawable getCategoryIcon() {
		return getPaintedIcon(R.drawable.ic_action_folder_stroke, getPointColor());
	}

	@Override
	public int getPointColor() {
		return color == 0 ? defaultColor : color;
	}

	private static class SaveGpxAsyncTask extends AsyncTask<Void, Void, Void> {
		private final OsmandApplication app;
		private final GPXFile gpx;
		private final boolean gpxSelected;

		SaveGpxAsyncTask(OsmandApplication app, GPXFile gpx, boolean gpxSelected) {
			this.app = app;
			this.gpx = gpx;
			this.gpxSelected = gpxSelected;
		}

		@Override
		protected Void doInBackground(Void... params) {
			GPXUtilities.writeGpxFile(new File(gpx.path), gpx, app);
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			if (!gpxSelected) {
				app.getSelectedGpxHelper().setGpxFileToDisplay(gpx);
			}
		}
	}
}
