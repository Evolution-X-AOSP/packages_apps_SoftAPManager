package org.exthmui.softap;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.exthmui.softap.model.ClientInfo;

public class ClientInfoActivity extends FragmentActivity implements SoftApManageService.StatusListener {

    private SoftApManageService.SoftApManageBinder mSoftApManageBinder;
    private SoftApManageConn mSoftApManageConn;

    private ClientInfoFragment mFragment;

    private String mMACAddress;
    private ClientInfo mClientInfo;
    private IClientManager mClientManager = new IClientManager() {
        @Override
        public boolean block(boolean val) {
            if (mSoftApManageBinder != null) {
                if (val) {
                    return mSoftApManageBinder.blockClient(mMACAddress);
                } else {
                    return mSoftApManageBinder.unblockClient(mMACAddress);
                }
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mMACAddress = intent.getStringExtra("mac");
        if (TextUtils.isEmpty(mMACAddress)) {
            finish();
            return;
        }

        setContentView(R.layout.client_info_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content, new ClientInfoFragment())
                    .commit();
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        Intent mSoftApManageService = new Intent(this, SoftApManageService.class);
        mSoftApManageConn = new SoftApManageConn();
        bindServiceAsUser(mSoftApManageService, mSoftApManageConn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT_OR_SELF);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (mSoftApManageConn != null) {
            unbindService(mSoftApManageConn);
        }
        if (mFragment != null) {
            mFragment.setClientManager(null);
        }
        super.onDestroy();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (mFragment == null && fragment instanceof ClientInfoFragment) {
            mFragment = (ClientInfoFragment) fragment;
            mFragment.setClientManager(mClientManager);
        }
    }

    private void updateClientInfo() {
        if (mFragment == null || mSoftApManageBinder == null) {
            return;
        }
        mClientInfo = mSoftApManageBinder.getClientByMAC(mMACAddress);
        mFragment.updateClientInfo(mClientInfo);
    }

    @Override
    public void onStatusChanged(int what) {

    }

    private class SoftApManageConn implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mSoftApManageBinder = (SoftApManageService.SoftApManageBinder) iBinder;
            updateClientInfo();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSoftApManageBinder = null;
            finish();
        }
    }


    public static class ClientInfoFragment extends PreferenceFragmentCompat implements
            CompoundButton.OnCheckedChangeListener {

        private IClientManager mClientManager;
        private Preference prefName;
        private Preference prefMAC;
        private Preference prefIP;
        private Preference prefManufacturer;
        private boolean mAllowed = true;

        private TextView mTextView;
        private View mSwitchBar;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.client_info_prefs, rootKey);
            prefName = findPreference("name");
            prefIP = findPreference("ip_address");
            prefMAC = findPreference("mac_address");
            prefManufacturer = findPreference("manufacturer");
        }

        @Override
        public View onCreateView(LayoutInflater inflater,
                ViewGroup container, Bundle savedInstanceState) {
            final View view = LayoutInflater.from(getContext()).inflate(R.layout.master_setting_switch, container, false);
            ((ViewGroup) view).addView(super.onCreateView(inflater, container, savedInstanceState));
            return view;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mTextView = view.findViewById(R.id.switch_text);
            mTextView.setText(getString(mAllowed ?
                    R.string.switch_on_text : R.string.switch_off_text));

            mSwitchBar = view.findViewById(R.id.switch_bar);
            Switch switchWidget = mSwitchBar.findViewById(android.R.id.switch_widget);
            switchWidget.setChecked(mAllowed);
            switchWidget.setOnCheckedChangeListener(this);
            mSwitchBar.setActivated(mAllowed);
            mSwitchBar.setOnClickListener(v -> {
                switchWidget.setChecked(!switchWidget.isChecked());
                mSwitchBar.setActivated(switchWidget.isChecked());
            });
        }

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
            if (mClientManager != null) {
                mClientManager.block(!isChecked);
            }
            mTextView.setText(getString(isChecked ? R.string.switch_on_text : R.string.switch_off_text));
            mSwitchBar.setActivated(isChecked);
        }

        public void setClientManager(IClientManager manager) {
            mClientManager = manager;
        }

        private void refreshSwitch() {
            Switch switchWidget = mSwitchBar.findViewById(android.R.id.switch_widget);
            switchWidget.setChecked(mAllowed);
            mSwitchBar.setActivated(mAllowed);
        }

        public void updateClientInfo(ClientInfo info) {
            if (info == null) return;
            prefName.setSummary(info.getName());
            prefMAC.setSummary(info.getMACAddress());
            prefIP.setSummary(String.join("\n", info.getIPAddressArray()));
            prefManufacturer.setSummary(info.getManufacturer());
            mAllowed = !info.isBlocked();
            refreshSwitch();
        }
    }

    protected interface IClientManager {
        boolean block(boolean val);
    }
}