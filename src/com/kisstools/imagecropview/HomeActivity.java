package com.kisstools.imagecropview;

import me.dawson.kisstools.KissTools;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class HomeActivity extends Activity {

	public static final String TAG = "HomeActivity";

	private static final int REQ_PICK_IMAGE = 1984;

	private ImageCropView cropView;

	private Button btSave, btChoose;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		KissTools.setContext(this);
		setContentView(R.layout.activity_home);

		btSave = (Button) findViewById(R.id.bt_save);
		btChoose = (Button) findViewById(R.id.bt_choose);

		btSave.setOnClickListener(listener);
		btChoose.setOnClickListener(listener);

		cropView = (ImageCropView) findViewById(R.id.iev_image);
	}

	private OnClickListener listener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (v.equals(btChoose)) {
				chooseImage();
			} else if (v.equals(btSave)) {
				saveImage();
			}
		}
	};

	private void chooseImage() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_PICK);
		String title = "Choose image";
		Intent chooser = Intent.createChooser(intent, title);
		startActivityForResult(chooser, REQ_PICK_IMAGE);
	}

	private void saveImage() {
		Bitmap bitmap = cropView.cropBitmap();
		cropView.setBitmap(bitmap);
	}

	public void onActivityResult(int reqCode, int resultCode, Intent data) {
		super.onActivityResult(reqCode, resultCode, data);

		if (resultCode != Activity.RESULT_OK) {
			Log.d(TAG, "user cancelled");
			return;
		}

		if (reqCode == REQ_PICK_IMAGE) {
			Uri selectedImage = data.getData();
			String[] filePathColumn = { MediaStore.Images.Media.DATA };

			Cursor cursor = getContentResolver().query(selectedImage,
					filePathColumn, null, null, null);
			cursor.moveToFirst();

			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String filePath = cursor.getString(columnIndex);
			cursor.close();
			cropView.setBitmap(filePath);
		}
	}

}
