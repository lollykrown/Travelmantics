package com.lollykrown.travelmantics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

public class DealActivity extends AppCompatActivity {
    private static final String TAG = DealActivity.class.getSimpleName();

    private FirebaseDatabase fd;
    private DatabaseReference dbr;
    private Uri mCurrentDealUri;
    private static final int PICTURE_RESULT = 42;
    private EditText txtTitle, txtDescr, txtPrice;
    private ImageView img;
    TravelDeal deal;
    Button btnImage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal);

        Intent intent = getIntent();
        mCurrentDealUri = intent.getData();

        TravelDeal deal = (TravelDeal) intent.getSerializableExtra("Deal");
        if (deal==null) {
            deal = new TravelDeal();
            // This is a new deal, so change the app bar to say "Add a new deal"
            setTitle("Add a new Deal");
            // Invalidate the options menu, so the "Delete" menu option can be hidden.
            // (so that we will not be deleting a deal that hasn't been created yet.)
            supportInvalidateOptionsMenu();
        } else {
            // Otherwise this is an existing deal, so change app bar to say "Edit deal"
            setTitle("Edit Deal");
        }

        fd = FirebaseUtil.fd;
        dbr = FirebaseUtil.dbr;
        txtTitle = findViewById(R.id.txtTitle);
        txtDescr = findViewById(R.id.txtDescr);
        txtPrice = findViewById(R.id.txtPrice);
        img = findViewById(R.id.image);

        this.deal = deal;
        txtTitle.setText(deal.getTitle());
        txtDescr.setText(deal.getDescription());
        txtPrice.setText(deal.getPrice());
        showImage(deal.getImageUrl());
        btnImage = findViewById(R.id.btnImage);
        btnImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                String[] mimeTypes = {"image/jpeg", "image/png"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(intent, PICTURE_RESULT);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.save_menu, menu);
        if (FirebaseUtil.isAdmin) {
            menu.findItem(R.id.delete_menu).setVisible(true);
            menu.findItem(R.id.save_menu).setVisible(true);
        }
        else {
            menu.findItem(R.id.delete_menu).setVisible(false);
            menu.findItem(R.id.save_menu).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_menu:
                saveDeal();
                Toast.makeText(this, "Deal saved", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            case R.id.delete_menu:
                showDeleteConfirmationDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICTURE_RESULT && resultCode == RESULT_OK) {
            final Uri imageUri = data.getData();
            final StorageReference ref = FirebaseUtil.mStorageRef.child(imageUri.getLastPathSegment());
            final UploadTask ut = ref.putFile(imageUri);

            ut.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                    ut.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                        @Override
                        public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                            if (!task.isSuccessful()) {
                                throw task.getException();

                            }
                            // Continue with the task to get the download URL
                            Log.e(TAG, ref.getDownloadUrl().toString());
                            return ref.getDownloadUrl();

                        }
                    }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                            if (task.isSuccessful()) {
                                String download_url = task.getResult().toString();
                                //String pictureName = taskSnapshot.getStorage().getPath();
                                deal.setImageUrl(download_url);
                                //deal.setImageName(pictureName);
                                Log.e(TAG, "THIS IS THE URL - " + download_url);
                                //Log.e(TAG, pictureName);
                                btnImage.setVisibility(View.GONE);
                                showImage(download_url);


                            }
                        }
                    });

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(DealActivity.this, "Upload failed" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void saveDeal() {
        deal.setTitle(txtTitle.getText().toString());
        deal.setDescription(txtDescr.getText().toString());
        deal.setPrice(txtPrice.getText().toString());
        if(deal.getId() == null) {
            dbr.push().setValue(deal);
        }
        else {
            dbr.child(deal.getId()).setValue(deal);
        }
    }
    private void deleteDeal() {
        if (deal == null) {
            Toast.makeText(this, "Please save the deal before deleting", Toast.LENGTH_SHORT).show();
            return;
        }
        dbr.child(deal.getId()).removeValue();
        //Log.d("image name", deal.getImageName());
        if(deal.getImageName() != null && !deal.getImageName().isEmpty()) {
            StorageReference picRef = FirebaseUtil.mStorage.getReference().child(deal.getImageName());
            picRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("Delete Image", "Image Successfully Deleted");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("Delete Image", e.getMessage());
                }
            });
        }
        finish();
    }

    private void showImage(String url) {
        if (url != null && !url.isEmpty()) {
            int width = Resources.getSystem().getDisplayMetrics().widthPixels;
//            Glide.with(DealActivity.this)
//                    .load(Uri.parse(url)) // add your image url
//                    .crossFade()
//                    .centerCrop()
//                    //.error(R.mipmap.ic_launcher)
//                    .override(width, width*2/3)
//                    .into(img);
            Picasso.with(this)
                    .load(url)
                    .resize(width, width*2/3)
                    .centerCrop()
                    .into(img);

        }
    }

    private void showDeleteConfirmationDialog() {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the postive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Delete this Deal?");
        builder.setPositiveButton(R.string.delete_deal, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Delete" button, so delete the deal.
                deleteDeal();
                Toast.makeText(DealActivity.this, "Deal Deleted", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                // and continue editing the deal.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
