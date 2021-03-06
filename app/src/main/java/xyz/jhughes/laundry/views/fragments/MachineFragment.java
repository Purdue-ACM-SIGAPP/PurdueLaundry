package xyz.jhughes.laundry.views.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import androidx.databinding.DataBindingUtil;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import xyz.jhughes.laundry.laundryparser.Constants;
import xyz.jhughes.laundry.laundryparser.Machine;
import xyz.jhughes.laundry.AnalyticsApplication;
import xyz.jhughes.laundry.ModelOperations;
import xyz.jhughes.laundry.R;
import xyz.jhughes.laundry.SnackbarPostListener;
import xyz.jhughes.laundry.viewmodels.MachineViewModel;
import xyz.jhughes.laundry.viewmodels.ViewModelFactory;
import xyz.jhughes.laundry.views.activities.LocationActivity;
import xyz.jhughes.laundry.views.adapters.MachineAdapter;
import xyz.jhughes.laundry.analytics.AnalyticsHelper;
import xyz.jhughes.laundry.analytics.ScreenTrackedFragment;
import xyz.jhughes.laundry.databinding.FragmentMachineBinding;
import xyz.jhughes.laundry.data.MachineAPI;
import xyz.jhughes.laundry.notificationhelpers.ScreenOrientationLockToggleListener;

import static xyz.jhughes.laundry.laundryparser.MachineStates.AVAILABLE;
import static xyz.jhughes.laundry.laundryparser.MachineStates.NOT_ONLINE;
import static xyz.jhughes.laundry.laundryparser.MachineStates.OUT_OF_ORDER;

public class MachineFragment extends ScreenTrackedFragment implements SwipeRefreshLayout.OnRefreshListener, SnackbarPostListener, ScreenOrientationLockToggleListener {

    private MachineViewModel machineViewModel;

    private FragmentMachineBinding binding;

    private List<Machine> classMachines;

    private MachineAdapter currentAdapter;
    @Inject
    MachineAPI machineAPI;

    @Inject
    ViewModelFactory viewModelFactory;

    private boolean isRefreshing;
    private boolean isDryers;
    private ProgressDialog progressDialog;

    private String mRoomName;

    private Call<List<Machine>> call = null;

    public MachineFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((AnalyticsApplication)getContext().getApplicationContext()).getAppComponent().inject(MachineFragment.this);
        machineViewModel = ViewModelProviders.of(this, viewModelFactory).get(MachineViewModel.class);
        progressDialog = new ProgressDialog(this.getContext());
        if (!isRefreshing) {
            progressDialog.setMessage(getString(R.string.loading_machines));
            progressDialog.show();
        }
        progressDialog.setCanceledOnTouchOutside(false);

        subscribeToErrorMessage();

        mRoomName = getArguments().getString("roomName");
        String machineType = (getArguments().getBoolean("isDryers")) ? "Dryers" : "Washers";
        setScreenName(Constants.getApiLocation(mRoomName) + ": " + machineType);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        isDryers = getArguments().getBoolean("isDryers");

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_machine, container, false);

        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this.getContext(), 2);
        binding.dryerMachinesRecyclerView.setLayoutManager(layoutManager);

        classMachines = new ArrayList<>();

        initializeNotifyOnAvaiableButton();
        refreshList();
        binding.dryerListLayout.setOnRefreshListener(this);

        return binding.getRoot();
    }

    @Override
    public void onRefresh() {
        isRefreshing = true;
        refreshList();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void refreshList() {
        if (isNetworkAvailable()) {
            String location = Constants.getApiLocation(mRoomName);

            machineViewModel.getMachines(location).observe(this, new Observer<List<Machine>>() {
                @Override
                public void onChanged(List<Machine> machines) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    binding.dryerListLayout.setRefreshing(false);
                    isRefreshing = false;
                    classMachines = machines;

                    if (ModelOperations.machinesOffline(classMachines)) {
                        showOfflineDialogIfNecessary();
                    }

                    updateRecyclerView();
                }
            });
        } else {
            showNoInternetDialog();
        }
    }

    public void updateRecyclerView() {
        MachineAdapter adapter = new MachineAdapter(classMachines, getContext(), isDryers, mRoomName, MachineFragment.this, MachineFragment.this);
        binding.dryerMachinesRecyclerView.setAdapter(adapter);
        currentAdapter = adapter;

        //Check if the view is being filtered and causing the
        // fragment to appear empty.
        // This is not shown if the list is empty for any other reason.
        if (currentAdapter.getCurrentMachines().isEmpty()) {
            //Filters are too restrictive.
            binding.machineFragmentTooFiltered.setVisibility(View.VISIBLE);
        } else {
            binding.machineFragmentTooFiltered.setVisibility(View.GONE);
        }

        boolean addNotifyButton = binding.machineFragmentNotifyButton.getVisibility() != View.VISIBLE;
        if (addNotifyButton) {
            for (Machine m : adapter.getAllMachines()) {
                if (m.getStatus().equalsIgnoreCase(AVAILABLE)) {
                    addNotifyButton = false;
                }
            }
            if (addNotifyButton) addNotifyOnAvailableButton();
            else removeNotifyOnAvailableButton();
        }
    }

    private void subscribeToErrorMessage() {
        this.machineViewModel.getError().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer errorMessageResourceId) {
                String errorMessage = getString(errorMessageResourceId);
                showErrorDialog(errorMessage);
            }
        });
    }

    private void showErrorDialog(final String message) {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        binding.dryerListLayout.setRefreshing(false);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle("Connection Error");
        alertDialogBuilder.setMessage(message);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent i = new Intent(getActivity(), LocationActivity.class).putExtra("forceMainMenu", true).putExtra("error", message);
                startActivity(i);
                getActivity().finish();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void alertNetworkError() {
        postSnackbar("There was an issue updating the machines, please try again later.", Snackbar.LENGTH_SHORT);
    }

    private void removeNotifyOnAvailableButton() {
        binding.machineFragmentNotifyButton.setVisibility(View.GONE);
    }

    private void addNotifyOnAvailableButton() {
        binding.machineFragmentNotifyButton.setVisibility(View.VISIBLE);
    }

    private void initializeNotifyOnAvaiableButton() {
        final String text = isDryers ? getString(R.string.notify_on_dryer_available) : getString(R.string.notify_on_washer_available);
        binding.machineFragmentNotifyButton.setText(text);
        binding.machineFragmentNotifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Machine m = null;
                int mTime = Integer.MAX_VALUE;
                for (Machine machine : currentAdapter.getAllMachines()) {
                    try {
                        int machineTime = Integer.parseInt(machine.getTime().substring(0, machine.getTime().indexOf(' ')));
                        if (machineTime < mTime) {
                            m = machine;
                            mTime = machineTime;
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                        continue;
                    }
                }
                if (m == null) {
                    postSnackbar(getString(R.string.fragment_no_machine), Snackbar.LENGTH_LONG);
                    return;
                }
                if (m.getStatus().equals(NOT_ONLINE) || m.getStatus().equals(OUT_OF_ORDER)) {
                    postSnackbar(getString(R.string.fragment_offline_location), Snackbar.LENGTH_LONG);
                    return;
                }
                currentAdapter.createNotification(m);
            }
        });
    }

    private void showOfflineDialogIfNecessary() {
        if (!getContext().getSharedPreferences("alerts", Context.MODE_PRIVATE).getBoolean("offline_alert_thrown", false)) {
            // 1. Instantiate an AlertDialog.Builder with its constructor
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            // 2. Chain together various setter methods to set the dialog characteristics
            builder.setMessage("We cannot reach the machines at this location right now, but they may still be available to use.")
                    .setTitle("Can't reach machines").setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            // 3. Get the AlertDialog from create()
            AlertDialog dialog = builder.create();

            dialog.show();
            getContext().getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().putBoolean("offline_alert_thrown", true).apply();
        }
    }

    private void showNoInternetDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle("Connection Error");
        alertDialogBuilder.setMessage("You have no internet connection");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent i = new Intent(getActivity(), LocationActivity.class).putExtra("forceMainMenu", true).putExtra("error", "You have no internet connection");
                startActivity(i);
                getActivity().finish();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    @Override
    public void onDetach() {
        if (call != null) {
            call.cancel();
        }

        super.onDetach();
    }

    @Override
    public void postSnackbar(String status, int length) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), status, length);
        snackbar.show();
    }

    //http://stackoverflow.com/a/14150037
    //locks the screen to the current rotation
    public void onLock() {
        int orientation = getActivity().getRequestedOrientation();
        int rotation = ((WindowManager) getActivity().getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case Surface.ROTATION_90:
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case Surface.ROTATION_180:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            default:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
        }
        getActivity().setRequestedOrientation(orientation);
    }

    public void onUnlock() {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
}
