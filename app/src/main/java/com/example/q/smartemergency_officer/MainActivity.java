package com.example.q.smartemergency_officer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MainActivity extends AppCompatActivity {

    AlertHandler handler = new AlertHandler();
    GoogleMap map = null;

    Socket socket;
    boolean isAlive;

    String unitID = null;
    String caseID = null;

    int phase = -1;

    private static int LOGIN_PHASE = 0;
    private static int STANDBY_PHASE = 1;
    private static int CASE_PHASE = 2;

    View login_layout = null;
    View standby_layout = null;
    View case_layout = null;

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0: {
                for (int i = 0; i < permissions.length; i++)
                    if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION))
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                            setupMap();
                break;
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (login_layout == null)
            login_layout = LayoutInflater.from(this).inflate(R.layout.login_layout, null);
        if (standby_layout == null)
            standby_layout = LayoutInflater.from(this).inflate(R.layout.standby_layout, null);
        if (case_layout == null)
            case_layout = LayoutInflater.from(this).inflate(R.layout.case_layout, null);

        isAlive = true;

        Initialization();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (phase == CASE_PHASE)
            if (!socket.connected()) {
                socket.connect();
                socket.emit("id", unitID);
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (socket != null && socket.connected())
            socket.disconnect();
        isAlive = false;
    }

    void Initialization() {
        phase = LOGIN_PHASE;
        setContentView(login_layout);
        findViewById(R.id.register_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RegisterDialog(MainActivity.this).show();
            }
        });
        findViewById(R.id.login_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String id = ((TextView) findViewById(R.id.id_field)).getText().toString();
                String pw = ((TextView) findViewById(R.id.pw_field)).getText().toString();

                if (id.length() == 0 || pw.length() == 0)
                    Toast.makeText(MainActivity.this, "모든 필드를 채워주세요.", Toast.LENGTH_SHORT).show();
                else {
                    Volley.newRequestQueue(MainActivity.this).add(new StringRequest("http://52.231.68.157:8080/private/login/" + id + "/" + pw, new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            if (response.equals("not found your id"))
                                Toast.makeText(MainActivity.this, "아이디 또는 비밀번호가 틀립니다.", Toast.LENGTH_SHORT).show();
                            else {
                                try {
                                    JSONObject response_json = new JSONObject(response);
                                    unitID = response_json.getString("unitId");
                                    caseID = response_json.getString("caseId");
                                    loggedInInitialization();
                                } catch (Exception e) {
                                    System.out.println(e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }
                    }, null));
                }
            }
        });
    }

    void loggedInInitialization() {

        try {
            socket = IO.socket("http://52.231.68.157:9090");
            socket.connect();
            socket.emit("id", unitID);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        socket.on("event occur", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (phase == STANDBY_PHASE) {
                    caseID = (String) args[0];
                    Message msg = new Message();
                    msg.arg1 = 1;
                    handler.sendMessage(msg);
                }
            }
        });

        if (caseID.equals("None")) {
            phase = STANDBY_PHASE;
            setContentView(standby_layout);
            standby_initialization();
        } else {
            phase = CASE_PHASE;
            setContentView(case_layout);
            case_initialization();
        }
    }

    void standby_initialization() {
        ((TextView) findViewById(R.id.standby_message)).setText("상황 대기중...");
        findViewById(R.id.accept_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.alert_blue).setVisibility(View.INVISIBLE);
        findViewById(R.id.alert_red).setVisibility(View.INVISIBLE);
    }

    private double lat = -1;
    private double lng = -1;
    private double reported_lat = -1;
    private double reported_lng = -1;
    private Marker marker;

    private ImageAdapter imageAdapter;
    private RecyclerView image_rec_view;

    void case_initialization_postLoc() {
        // Layout initialization
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getView().getLayoutParams().width = point.x - 40 * (int) (((float) getResources().getDisplayMetrics().densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
        mapFragment.getView().getLayoutParams().height = point.x * 3 / 4;

        // Map and permission initialization - START
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                map = googleMap;
                googleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(37.549573, 126.989079)));
                if(marker==null) {
                    marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(reported_lat, reported_lng)));
                }
                else {
                    marker.setPosition(new LatLng(reported_lat, reported_lng));
                }

                // Permission check
                ArrayList<String> permissionList = new ArrayList<>();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
                else
                    setupMap();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                    permissionList.add(Manifest.permission.CALL_PHONE);

                String[] permissionRequests = new String[permissionList.size()];
                for (int i = 0; i < permissionRequests.length; i++) {
                    permissionRequests[i] = permissionList.get(i);
                }
                if (permissionRequests.length > 0)
                    ActivityCompat.requestPermissions(MainActivity.this, permissionRequests, 0);

            }
        });
        // Map and permission initialization - END


        // Setup image recycler view
        image_rec_view = findViewById(R.id.image_rec_view);
        imageAdapter = new ImageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        image_rec_view.setAdapter(imageAdapter);
        image_rec_view.setLayoutManager(layoutManager);
        downloadImages("http://52.231.68.157:8080/private/report/getImagePath/" + caseID, imageAdapter);

        // Setup socket for image add
        socket.on("image upload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject json = (JSONObject) args[0];
                try {
                    new downTask(json.getString("image_path"), Integer.parseInt(json.getString("exif")), imageAdapter).execute();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
            }
        });


        // Setup end_case_button
        findViewById(R.id.end_case_button).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                isAlive = !isAlive;
                Volley.newRequestQueue(MainActivity.this).add(new StringRequest("http://52.231.68.157:8080/private/report/end/" + caseID, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (response.equals("Complete")) {
                            Toast.makeText(MainActivity.this, "사건이 종결되었습니다.", Toast.LENGTH_SHORT).show();
                            Initialization();
                        } else {
                            Toast.makeText(MainActivity.this, "네트워크를 확인하세요.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.out.println("error : " + error.getMessage());
                    }
                }));
            }
        });
    }

    void case_initialization() {

        ((TextView) findViewById(R.id.case_field)).setText("진행중인 사건(id) : " + caseID);

        Volley.newRequestQueue(this).add(new JsonObjectRequest("http://52.231.68.157:8080/private/case_loc/" + caseID, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    reported_lat = Double.parseDouble(response.getString("lat"));
                    reported_lng = Double.parseDouble(response.getString("lng"));
                    case_initialization_postLoc();
                }catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        }));
    }

    void setupMap() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(MainActivity.this, "위치 권한이 없습니다. 설정에서 권한 요청을 승인해주세요.", Toast.LENGTH_SHORT).show();
        else {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            getFusedLocationProviderClient(MainActivity.this).getLastLocation().addOnCompleteListener(MainActivity.this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location location = task.getResult();
                    map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
                }
            });
            map.moveCamera(CameraUpdateFactory.zoomTo(17));
        }

        new Thread() {
            @Override
            public void run() {
                super.run();
                while (isAlive) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        Toast.makeText(MainActivity.this, "위치 권한이 없습니다. 설정에서 권한 요청을 승인해주세요.", Toast.LENGTH_SHORT).show();
                    else {
                        getFusedLocationProviderClient(MainActivity.this).getLastLocation().addOnCompleteListener(MainActivity.this, new OnCompleteListener<Location>() {
                            @Override
                            public void onComplete(@NonNull Task<Location> task) {
                                Location location = task.getResult();
                                lat = location.getLatitude();
                                lng = location.getLongitude();
                                Volley.newRequestQueue(MainActivity.this).add(new StringRequest(Request.Method.POST, "http://52.231.68.157:8080/private/sendLocate/" + unitID, new Response.Listener<String>() {
                                    @Override
                                    public void onResponse(String response) {
                                        if (!response.equals("unit locate ok")) {
                                            System.out.println(response);
                                            Toast.makeText(MainActivity.this, "네트워크를 확인하세요.", Toast.LENGTH_SHORT).show();
                                        }
                                        System.out.println(response);
                                    }
                                }, new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        System.out.println("Error : " + error.getMessage());
                                    }
                                }) {
                                    @Override
                                    protected Map<String, String> getParams() throws AuthFailureError {
                                        Map<String, String> map = new HashMap<>();
                                        map.put("lat", Double.toString(lat));
                                        map.put("lng", Double.toString(lng));
                                        return map;
                                    }
                                });
                            }
                        });
                    }
                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    class AlertHandler extends Handler {
        boolean isRed = true;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 0) {
                if (isRed) {
                    findViewById(R.id.alert_blue).setVisibility(View.VISIBLE);
                    findViewById(R.id.alert_red).setVisibility(View.INVISIBLE);
                } else {
                    findViewById(R.id.alert_blue).setVisibility(View.INVISIBLE);
                    findViewById(R.id.alert_red).setVisibility(View.VISIBLE);
                }
                isRed = !isRed;
            }
            if (msg.arg1 == 1) {
                ((TextView) findViewById(R.id.standby_message)).setText("상황 발생");
                findViewById(R.id.accept_button).setVisibility(View.VISIBLE);
                findViewById(R.id.accept_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        phase = CASE_PHASE;
                        setContentView(case_layout);
                        case_initialization();
                    }
                });

                new Thread() {


                    @Override
                    public void run() {
                        super.run();
                        while (phase == STANDBY_PHASE) {
                            Message msg = new Message();
                            msg.arg1 = 0;
                            handler.sendMessage(msg);
                            try {
                                sleep(250);
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }

                }.start();
            }
        }
    }

    class RegisterDialog extends Dialog {

        public RegisterDialog(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.register_layout);
            findViewById(R.id.do_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String id = ((TextView) findViewById(R.id.id_field)).getText().toString();
                    String pw = ((TextView) findViewById(R.id.pw_field)).getText().toString();
                    String pw2 = ((TextView) findViewById(R.id.pw_field2)).getText().toString();
                    if (id.length() == 0 || pw.length() == 0 || pw2.length() == 0)
                        Toast.makeText(getContext(), "모든 필드를 채워주세요.", Toast.LENGTH_SHORT).show();
                    else if (!pw.equals(pw2))
                        Toast.makeText(getContext(), "비밀번호를 다시 입력해주세요.", Toast.LENGTH_SHORT).show();
                    else {
                        Volley.newRequestQueue(getContext()).add(new StringRequest("http://52.231.68.157:8080/private/register/" + id + "/" + pw, new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                if (response.equals("id duplicate"))
                                    Toast.makeText(getContext(), "아이디가 이미 존재합니다.", Toast.LENGTH_SHORT).show();
                                else {
                                    Toast.makeText(getContext(), "승인되었습니다. 로그인 해주세요.", Toast.LENGTH_SHORT).show();
                                    cancel();
                                }
                            }
                        }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                System.out.println(error.getMessage());
                            }
                        }));
                    }

                }
            });
            findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cancel();
                }
            });
        }
    }


    public static ArrayList<Bitmap> bitmaps;

    class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

        ImageAdapter() {
            super();
            bitmaps = new ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new ViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.image_viewholder, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, final int i) {
            Glide.with(viewHolder.itemView.getContext()).load(bitmaps.get(i)).into(viewHolder.image);
            viewHolder.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this, ImageShowActivity.class);
                    try {
                        intent.putExtra("data", i);
                        startActivity(intent);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return bitmaps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ImageView image;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.image);
            }
        }
    }

    //IMAGE LOAD
    public class downTask extends AsyncTask<Void, Void, Void> {

        String path;
        ImageAdapter imageAdapter;
        int exifDegree = -1;

        downTask(String _path, int _exifDegree, ImageAdapter _imageAdapter) {
            path = _path;
            exifDegree = _exifDegree;
            imageAdapter = _imageAdapter;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
//            imageView.setImageBitmap(bitmap);
//            imageView.invalidate();
            super.onPostExecute(aVoid);
//            imageAdapter.notifyItemInserted(imageAdapter.getItemCount()-1);
            image_rec_view.setAdapter(imageAdapter);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            getimage("http://52.231.68.157:8080/private/report/getImage", path, exifDegree);
            return null;
        }
    }

    public void downloadImages(String url, final ImageAdapter imageAdapter) {
        Volley.newRequestQueue(this).add(new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONArray arr = new JSONArray(response);
                    JSONArray paths = arr.getJSONArray(0);
                    JSONArray exifs = arr.getJSONArray(1);
                    for (int i = 0; i < paths.length(); i++) {
                        new downTask(paths.getString(i), exifs.getInt(i), imageAdapter).execute();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        }));
    }

    public void getimage(String url, String path, int exifDegree) {

        try {
            URL imgUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) imgUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject body = new JSONObject();
            body.put("image_path", path);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
            dos.writeBytes(body.toString());
            dos.flush();
            dos.close();


            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = conn.getInputStream();
                bitmaps.add(0, rotateBitmap(BitmapFactory.decodeStream(inputStream), exifDegree));
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}


