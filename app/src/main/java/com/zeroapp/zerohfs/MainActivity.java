package com.zeroapp.zerohfs;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import moe.feng.common.view.breadcrumbs.BreadcrumbsView;
import moe.feng.common.view.breadcrumbs.DefaultBreadcrumbsCallback;
import moe.feng.common.view.breadcrumbs.model.BreadcrumbItem;

public class MainActivity extends AppCompatActivity {
    private String serverUrl = "about:blank";
    private String currentPath = "/";
    private RequestQueue requestQueue;
    private ListView filesListView;
    private JSONObject filesJson;
    private ArrayList filesList;
    private BreadcrumbsView breadcrumbsView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Integer dirDeep = 1;
    private ExtendedFloatingActionButton uploadFab;
    private ProgressDialog processProgressDialog;
    private DownloadManager downloadManager;
    private String account,password;
    private static final String DataBaseName = "DataBaseIt";
    private static final int DataBaseVersion = 1;
    private static String DataBaseTable = "Servers";
    private static SQLiteDatabase db;
    private Integer serverId;
    private MaterialToolbar topAppBar;
    public static String getParentDirPath(String fileOrDirPath) {
        boolean endsWithSlash = fileOrDirPath.endsWith(File.separator);
        return fileOrDirPath.substring(0, fileOrDirPath.lastIndexOf(File.separatorChar,
                endsWithSlash ? fileOrDirPath.length() - 2 : fileOrDirPath.length() - 1));
    }
    private void switchProfile(int id) {
        Cursor c = db.rawQuery("SELECT * FROM " + DataBaseTable,null);
        for (int i = 0;i < id;i++) {
            try {
                c.moveToNext();
                serverId = c.getInt(0);
                serverUrl = c.getString(1);
                account = c.getString(2);
                password = c.getString(3);
            } catch (Exception e) {
                switchProfile(1);
            }
        }
        c.close();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SQLDataBaseHelper sqlDataBaseHelper = new SQLDataBaseHelper(this, DataBaseName, null, DataBaseVersion, DataBaseTable);
        db = sqlDataBaseHelper.getWritableDatabase();
        processProgressDialog = new ProgressDialog(MainActivity.this);
        processProgressDialog.setMessage(getString(R.string.processing_changes  ));
        processProgressDialog.setCancelable(false);
        processProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        uploadFab = findViewById(R.id.uploadFab);
        uploadFab.hide();
        uploadFab.setOnClickListener(v -> {
            Intent chooseFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            chooseFile.setType("*/*");
            chooseFile.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(chooseFile, 0);
        });
        topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.accountOptions:
                    boolean isLogin = !(account == null) && !(password == null);
                    AlertDialog.Builder accountOptionsDialog = new AlertDialog.Builder(MainActivity.this);
                    accountOptionsDialog.setTitle(isLogin ? account : getString(R.string.guest));
                    String[] accountOptions;
                    if (isLogin) {
                        accountOptions = new String[]{getString(R.string.logout)};
                    } else {
                        accountOptions = new String[]{getString(R.string.login)};
                    }
                    accountOptionsDialog.setItems(accountOptions, (dialog, which) -> {
                        if (isLogin){
                            if (which == 0) {
                                account = null;
                                password = null;
                                ContentValues contentValues = new ContentValues();
                                contentValues.putNull("account");
                                contentValues.putNull("password");
                                db.update(DataBaseTable,contentValues,"_id="+serverId,null);
                                loadFolder();
                            }
                        } else {
                            if (which == 0) {
                                doLogin();
                            }
                        }
                    });
                    accountOptionsDialog.show();
                    return true;
                case R.id.serverManager:
                    openServerManager();
                    return true;
                default:
                    return false;
            }
        });
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> loadFolder());
        breadcrumbsView = findViewById(R.id.breadcrumbs_view);
        breadcrumbsView.addItem(new BreadcrumbItem(Collections.singletonList("/")));
        breadcrumbsView.setCallback(new DefaultBreadcrumbsCallback<BreadcrumbItem>() {
            @Override
            public void onNavigateBack(BreadcrumbItem item, int position) {
                position+=1;
                for (int i = 0;i < dirDeep-position;i++) {
                    try {
                        currentPath = getParentDirPath(currentPath);
                    } catch (Exception e) {
                        currentPath = "/";
                    }
                }
                dirDeep=position;
                loadFolder();
            }

            @Override
            public void onNavigateNewLocation(BreadcrumbItem newItem, int changedPosition) {

            }
        });
        filesListView = findViewById(R.id.filesListView);
        filesListView.setOnItemClickListener((parent, view, position, id) -> onFilesListItemClicked(position));
        requestQueue = Volley.newRequestQueue(this);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (DatabaseUtils.queryNumEntries(db,"Servers")!=0) {
            switchProfile(1);
            topAppBar.setTitle(getString(R.string.connecting));
            topAppBar.setSubtitle(serverUrl);
            loadFolder();
        } else {
            openAddServerDialog(new String[0],true);
        }
    }
    public void onFilesListItemClicked(int position) {
        if (filesList!=null) {
            String fileUrl = serverUrl+filesList.get(position).toString();
            try {
                JSONObject fileInfo = filesJson.getJSONObject(String.valueOf(filesList.get(position)));
                String fileType = fileInfo.getString("filetype");
                if (fileType.equals("folder")) {
                    breadcrumbsView.addItem(new BreadcrumbItem(Collections.singletonList(filesList.get(position).toString().replace(currentPath, "").replace("/", ""))));
                    currentPath = (String) filesList.get(position);
                    dirDeep+=1;
                    loadFolder();
                } else {
                    processProgressDialog.show();
                    StringRequest oneTimeAccessTokenRequest = new StringRequest(Request.Method.GET, addLoginArgs(fileUrl+"?action=getOneTimeAccessToken"), response -> {
                        processProgressDialog.dismiss();
                        String newFileUrl = fileUrl+"?oneTimeAccessToken="+response;
                        if (fileType.equals("video")) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(newFileUrl), "video/mp4");
                            startActivity(intent);
                        } else if (fileType.equals("image")) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(newFileUrl), "image/*");
                            startActivity(intent);
                        } else {
                            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                            customTabsIntent.launchUrl(MainActivity.this, Uri.parse(newFileUrl));
                        }
                    }, error -> {
                        processProgressDialog.dismiss();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.unable_to_process_changes))
                                .setPositiveButton(getString(android.R.string.ok), null)
                                .show();
                    });
                    oneTimeAccessTokenRequest.setShouldCache(false);
                    requestQueue.add(oneTimeAccessTokenRequest);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
    private void doLogin() {
        AlertDialog.Builder loginDialog = new AlertDialog.Builder(MainActivity.this);
        loginDialog.setTitle(getString(R.string.login));
        LayoutInflater inflater = getLayoutInflater();
        View loginView = inflater.inflate(R.layout.login_dialog, null);
        loginDialog.setView(loginView);
        loginDialog.setNegativeButton(getString(android.R.string.cancel), null);
        loginDialog.setPositiveButton(getString(android.R.string.ok), (dialog, which) -> {
            account = ((TextInputLayout)loginView.findViewById(R.id.accountTextField)).getEditText().getText().toString();
            password = ((TextInputLayout)loginView.findViewById(R.id.passwordTextField)).getEditText().getText().toString();
            ContentValues contentValues = new ContentValues();
            contentValues.put("account", account);
            contentValues.put("password",password);
            db.update(DataBaseTable,contentValues,"_id="+serverId,null);
            loadFolder();
        });
        loginDialog.show();
    }

    private void loadFolder() {
        swipeRefreshLayout.setRefreshing(true);
        filesListView.setAdapter(null);
        filesList = new ArrayList<>();
        String reqUrl = addLoginArgs(serverUrl + currentPath + "?format=json");
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, reqUrl, null, response -> {
            filesJson = response;
            List<HashMap<String,String>> hmFiles = new ArrayList<>();
            if (response.has("status")) {
                try {
                    if (response.getString("msg").equals("login failed")) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.invalid_login_session))
                                .setCancelable(false)
                                .setNegativeButton(getString(R.string.use_guest_account), (dialog, which) -> {
                                    account = null;
                                    password = null;
                                    ContentValues contentValues = new ContentValues();
                                    contentValues.putNull("account");
                                    contentValues.putNull("password");
                                    db.update(DataBaseTable,contentValues,"_id="+serverId,null);
                                    loadFolder();
                                })
                                .setPositiveButton(getString(R.string.relogin), (dialog, which) -> doLogin())
                                .setNeutralButton(getString(R.string.retry), (dialog, which) -> loadFolder())
                                .show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    if (response.getBoolean("canUpload")) {
                        uploadFab.show();
                    } else {
                        uploadFab.hide();
                    }
                    topAppBar.setTitle(((JSONObject)response.get("serverInfo")).getString("serverName"));
                } catch (JSONException e) {
                    uploadFab.hide();
                }
                for (Iterator<String> keys = response.keys(); keys.hasNext(); ) {
                    try {
                        HashMap<String, String> file = new HashMap<String, String>();
                        String key = keys.next();
                        if (response.get(key) instanceof JSONObject) {
                            JSONObject fileResp = (JSONObject) response.get(key);
                            filesList.add(fileResp.getString("name"));
                            String fileType = fileResp.getString("filetype");
                            switch (fileType) {
                                case "folder":
                                    file.put("icon", String.valueOf(R.drawable.ic_outline_folder_32));
                                    break;
                                case "video":
                                    file.put("icon", String.valueOf(R.drawable.ic_outline_movie_32));
                                    break;
                                case "image":
                                    file.put("icon", String.valueOf(R.drawable.ic_outline_image_32));
                                    break;
                                default:
                                    file.put("icon", String.valueOf(R.drawable.ic_outline_insert_drive_file_32));
                                    break;
                            }
                            file.put("name", fileResp.getString("name").replace(currentPath, "").replace("/", ""));
                            if (fileResp.getString("filesize").equals("")) {
                                file.put("info", fileResp.getString("modifyTime"));
                            } else {
                                file.put("info", fileResp.getString("modifyTime") + "  |  " + fileResp.getString("filesize"));
                            }
                            hmFiles.add(file);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                SimpleAdapter filesAdapter = new FilesAdapter(MainActivity.this, hmFiles, R.layout.file_list_item, new String[]{"icon","name", "info"}, new int[]{R.id.iconIV,R.id.nameTV, R.id.infoTV});
                filesListView.setAdapter(filesAdapter);
            }
            swipeRefreshLayout.setRefreshing(false);
        }, errorResp -> {
            filesJson = null;
            filesList = null;
            List<HashMap<String,String>> hmErrors = new ArrayList<>();
            try {
                int statusCode = errorResp.networkResponse.statusCode;
                if (statusCode == 401) {
                    HashMap<String,String> error = new HashMap<>();
                    error.put("title", getString(R.string.access_denied));
                    error.put("info", getString(R.string.access_denied_tip));
                    hmErrors.add(error);
                    topAppBar.setTitle(getString(R.string.app_name));
                } else {
                    HashMap<String,String> error = new HashMap<>();
                    error.put("title", getString(R.string.unknown_error_occurred));
                    error.put("info", getString(R.string.pull_down_to_retry));
                    hmErrors.add(error);
                    topAppBar.setTitle(getString(R.string.app_name));
                }
            } catch (Exception e) {
                HashMap<String,String> error = new HashMap<>();
                error.put("title", getString(R.string.unknown_error_occurred));
                error.put("info", getString(R.string.pull_down_to_retry));
                hmErrors.add(error);
                topAppBar.setTitle(getString(R.string.app_name));
            }

            SimpleAdapter errorsAdapter = new SimpleAdapter(MainActivity.this, hmErrors, R.layout.error_list_item, new String[]{"title", "info"}, new int[]{R.id.errorTitleTV, R.id.infoTV});
            filesListView.setAdapter(errorsAdapter);
            swipeRefreshLayout.setRefreshing(false);
        });
        jsonObjectRequest.setShouldCache(false);
        requestQueue.add(jsonObjectRequest);
    }
    private void openAddServerDialog(String[] serverUrlArray,boolean initMode) {
        AlertDialog.Builder addServerDialog = new AlertDialog.Builder(MainActivity.this);
        addServerDialog.setTitle(getString(R.string.add_server));
        LayoutInflater inflater = getLayoutInflater();
        View addServerView = inflater.inflate(R.layout.add_server_dialog, null);
        addServerDialog.setView(addServerView);
        addServerDialog.setOnCancelListener(dialog -> {openServerManager();});
        addServerDialog.setCancelable(!initMode);
        if (!initMode) {
            addServerDialog.setNegativeButton(getString(android.R.string.cancel), (dialog, which) -> {openServerManager();});
        }
        addServerDialog.setPositiveButton(getString(android.R.string.ok), (dialog2, which2) -> {
            String newServerUrl = ((TextInputLayout)addServerView.findViewById(R.id.serverUrlTextField)).getEditText().getText().toString();
            if (!URLUtil.isValidUrl(newServerUrl)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.invalid_server_address))
                        .setOnDismissListener(initMode ? dialog13 -> openAddServerDialog(new String[0],true) : dialog13 -> openServerManager())
                        .setPositiveButton(getString(android.R.string.ok), null)
                        .show();
            } else {
                try {
                    newServerUrl = newServerUrl.substring(0, newServerUrl.indexOf("/", newServerUrl.indexOf("/", newServerUrl.indexOf("/") + 1) + 1));
                } catch (Exception e) {

                }
                if (Arrays.asList(serverUrlArray).contains(newServerUrl)){
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(getString(R.string.server_exists))
                            .setOnDismissListener(dialog14 -> openServerManager())
                            .setPositiveButton(getString(android.R.string.ok), null)
                            .show();
                } else {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("serverUrl", newServerUrl);
                    contentValues.putNull("account");
                    contentValues.putNull("password");
                    db.insert(DataBaseTable, null, contentValues);
                    if (initMode) {
                        switchProfile(1);
                        topAppBar.setTitle(getString(R.string.connecting));
                        topAppBar.setSubtitle(serverUrl);
                        loadFolder();

                    } else {
                        openServerManager();
                    }
                }
            }
        });
        addServerDialog.show();
    }
    private void openServerManager() {
        Cursor c = db.rawQuery("SELECT * FROM " + DataBaseTable,null);
        String[] serverUrlArray = new String[c.getCount()];
        Integer[] serverIdArray = new Integer[c.getCount()];
        String[] accountArray = new String[c.getCount()];
        String[] passwordArray = new String[c.getCount()];
        c.moveToFirst();
        for(int i=0;i<c.getCount();i++){
            serverIdArray[i] = c.getInt(0);
            serverUrlArray[i] = c.getString(1);
            accountArray[i] = c.getString(2);
            passwordArray[i] = c.getString(3);
            c.moveToNext();
        }
        String[] serverUrlArrayDisplay = new String[serverUrlArray.length];
        for (int i = 0;i < serverUrlArray.length; i++) {
            if (accountArray[i]==null) {
                serverUrlArrayDisplay[i] = serverUrlArray[i] + "\n" + getString(R.string.login_identity) + getString(R.string.guest) + "\n";
            } else {
                serverUrlArrayDisplay[i] = serverUrlArray[i] + "\n" + getString(R.string.login_identity) + accountArray[i] + "\n";
            }
        }
        c.close();
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.server_manager))
                .setPositiveButton(getString(R.string.add_server), (dialog1, which1) -> {
                    openAddServerDialog(serverUrlArray,false);
                })
                .setItems(serverUrlArrayDisplay, (dialog, which) -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(serverUrlArrayDisplay[which])
                            .setOnCancelListener(dialog12 -> openServerManager())
                            .setItems(new String[]{getString(R.string.connect),getString(R.string.delete)}, (DialogInterface.OnClickListener) (dialog1, which1) -> {
                                switch (which1) {
                                    case 0:
                                        serverId = serverIdArray[which];
                                        serverUrl = serverUrlArray[which];
                                        account = accountArray[which];
                                        password = passwordArray[which];
                                        currentPath = "/";
                                        topAppBar.setTitle(getString(R.string.connecting));
                                        topAppBar.setSubtitle(serverUrl);
                                        breadcrumbsView.removeItemAfter(1);
                                        loadFolder();
                                        break;
                                    case 1:
                                        db.delete(DataBaseTable,"_id="+serverIdArray[which],null);
                                        openServerManager();
                                        break;
                                }
                            })
                            .show();
                })
                .show();
    }
    private String addLoginArgs(String url) {
        if (!(account == null) && !(password == null)) {
            url += "&account=" + account + "&password=" + password;
        } else {
            url += "&account=0";
        }
        return url;
    }
    public void fileMoreOptions(int pos) {
        String reqPath = (String) filesList.get(pos);
        boolean canDelete = false;
        try {
            canDelete = ((JSONObject) filesJson.get(reqPath)).getBoolean("canDelete");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        List<HashMap<String,String>> optionsList = new ArrayList<>();
        HashMap<String,String> option = new HashMap<>();
        option.put("icon", String.valueOf(R.drawable.ic_baseline_download_24));
        option.put("title", getString(R.string.download));
        optionsList.add(option);
        if (canDelete) {
            HashMap<String,String> option2 = new HashMap<>();
            option2.put("icon", String.valueOf(R.drawable.ic_baseline_delete_24));
            option2.put("title", getString(R.string.delete));
            optionsList.add(option2);
        }
        View fileOptionsDialogView = getLayoutInflater().inflate(R.layout.file_options_dialog_layout, null, false);
        ListView fileOptionsListView = fileOptionsDialogView.findViewById(R.id.list_view);
        fileOptionsListView.setAdapter(new SimpleAdapter(this, optionsList, R.layout.file_options_dialog_item, new String[]{"icon","title"}, new int[]{R.id.iv1,R.id.tv1}));
        AlertDialog.Builder fileOptionsDialog = new AlertDialog.Builder(MainActivity.this);
        fileOptionsDialog.setTitle(reqPath.replace(currentPath, "").replace("/", ""));
        fileOptionsDialog.setView(fileOptionsDialogView);
        AlertDialog fileOptionsDialogAlert = fileOptionsDialog.create();
        fileOptionsListView.setOnItemClickListener((parent, view, position, id) -> {
            fileOptionsDialogAlert.dismiss();
            if (position == 0) {
                try {
                    if (((JSONObject) filesJson.get(reqPath)).getString("filetype").equals("folder")) {
                        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                        customTabsIntent.launchUrl(MainActivity.this, Uri.parse(addLoginArgs(serverUrl+reqPath+"?action=download")));
                    } else {
                        Uri downloadUri = Uri.parse(addLoginArgs(serverUrl+reqPath+"?from=ZHFSAndroidClient"));
                        DownloadManager.Request downloadRequest = new DownloadManager.Request(downloadUri);
                        downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,reqPath.replace(currentPath, "").replace("/", ""));
                        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        downloadManager.enqueue(downloadRequest);
                    }
                } catch (JSONException e) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(getString(R.string.unable_to_process_changes))
                            .setPositiveButton(getString(android.R.string.ok), null)
                            .show();
                }
            } else if (position == 1) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.confirm_deletion_dialog_title))
                        .setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {
                            processProgressDialog.show();
                            JsonObjectRequest deleteRequest = new JsonObjectRequest(Request.Method.POST,addLoginArgs(serverUrl+reqPath+"?action=delete&format=json"),null, response -> {
                                processProgressDialog.dismiss();
                                loadFolder();
                            }, errorResp -> {
                                processProgressDialog.dismiss();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(getString(R.string.unable_to_process_changes))
                                        .setPositiveButton(getString(android.R.string.ok), null)
                                        .show();
                            });
                            deleteRequest.setShouldCache(false);
                            requestQueue.add(deleteRequest);
                        })
                        .setNegativeButton(getString(android.R.string.cancel),null)
                        .show();
            }
        });
        fileOptionsDialogAlert.show();
        fileOptionsDialogAlert.getWindow().setGravity(Gravity.BOTTOM);
    }
    @Override
    public void onBackPressed() {
        if ("/".equals(currentPath)) {
            super.onBackPressed();
        } else {
            currentPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
            if (currentPath.equals("")) {
                currentPath="/";
            }
            breadcrumbsView.removeLastItem();
            loadFolder();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==0 && resultCode == Activity.RESULT_OK) {
            try {
                new MultipartUploadRequest(MainActivity.this,addLoginArgs(serverUrl+currentPath+"?action=upload"))
                        .addFileToUpload(data.getData().toString(), "file")
                        .setMaxRetries(2)
                        .subscribe(MainActivity.this, MainActivity.this, new RequestObserverDelegate() {
                            @Override
                            public void onProgress(@NonNull Context context, @NonNull UploadInfo uploadInfo) {

                            }

                            @Override
                            public void onSuccess(@NonNull Context context, @NonNull UploadInfo uploadInfo, @NonNull ServerResponse serverResponse) {
                                loadFolder();
                            }

                            @Override
                            public void onError(@NonNull Context context, @NonNull UploadInfo uploadInfo, @NonNull Throwable throwable) {

                            }

                            @Override
                            public void onCompleted(@NonNull Context context, @NonNull UploadInfo uploadInfo) {

                            }

                            @Override
                            public void onCompletedWhileNotObserving() {

                            }
                        });
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}