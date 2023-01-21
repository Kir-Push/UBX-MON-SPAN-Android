package com.ublox.gnss;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ftdi.j2xx.D2xxManager;
import com.google.android.material.snackbar.Snackbar;
import com.ublox.gnss.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                try {
//                    int devCount = D2xxManager.getInstance(view.getContext().getApplicationContext()).createDeviceInfoList(view.getContext().getApplicationContext());
//                    D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
//                    int deviceInfoList = D2xxManager.getInstance(view.getContext().getApplicationContext()).getDeviceInfoList(devCount, deviceList);
//                    Snackbar.make(view, "Replace with your own action + = " + deviceInfoList + " " + devCount, Snackbar.LENGTH_LONG)
//                            .setAction("Action", null).show();
//                } catch (D2xxManager.D2xxException e) {
//                    e.printStackTrace();
//                }
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}