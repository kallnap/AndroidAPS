package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import de.jotomo.ruffy.spi.PumpState;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.DateUtil;

public class ComboFragment extends SubscriberFragment implements View.OnClickListener, View.OnLongClickListener {
    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView tempBasalText;
    private LinearLayout buttonsLayout;
    private TextView queueView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        stateView = (TextView) view.findViewById(R.id.combo_state);
        activityView = (TextView) view.findViewById(R.id.combo_activity);
        batteryView = (TextView) view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = (TextView) view.findViewById(R.id.combo_insulinstate);
        lastConnectionView = (TextView) view.findViewById(R.id.combo_lastconnection);
        //lastBolusView = (TextView) view.findViewById(R.id.combo_last_bolus);
        tempBasalText = (TextView) view.findViewById(R.id.combo_temp_basal);
        buttonsLayout = (LinearLayout) view.findViewById(R.id.combo_buttons_layout);
        queueView = (TextView) view.findViewById(R.id.combo_queue);


        Button refresh = (Button) view.findViewById(R.id.combo_refresh);
        refresh.setOnClickListener(this);

        Button errorHistory = (Button) view.findViewById(R.id.combo_error_history);
        errorHistory.setOnClickListener(this);

        Button tddHistory = (Button) view.findViewById(R.id.combo_tdd_history);
        tddHistory.setOnClickListener(this);
        tddHistory.setOnLongClickListener(this);

        Button fullHistory = (Button) view.findViewById(R.id.combo_full_history);
        fullHistory.setOnClickListener(this);
        fullHistory.setOnLongClickListener(this);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                new Thread(() -> ComboPlugin.getPlugin().getPumpStatus()).start();
                break;
            case R.id.combo_error_history:
                ComboAlertHistoryDialog ehd = new ComboAlertHistoryDialog();
                ehd.show(getFragmentManager(), ComboAlertHistoryDialog.class.getSimpleName());
                break;
            case R.id.combo_tdd_history:
                ComboTddHistoryDialog thd = new ComboTddHistoryDialog();
                thd.show(getFragmentManager(), ComboTddHistoryDialog.class.getSimpleName());
                break;
            case R.id.combo_full_history:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.combo_warning);
                builder.setMessage(R.string.combo_read_full_history_warning);
                builder.show();
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.combo_error_history:
                new Thread(() -> ComboPlugin.getPlugin().readAlertData()).start();
                return true;
            case R.id.combo_tdd_history:
                new Thread(() -> ComboPlugin.getPlugin().readTddData()).start();
                return true;
            case R.id.combo_full_history:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.combo_warning);
                builder.setMessage(R.string.combo_read_full_history_confirmation);
                builder.setPositiveButton(R.string.ok, (dialog, which) ->
                        new Thread(() -> ComboPlugin.getPlugin().readAllPumpData()).start());
                builder.setNegativeButton(MainApp.sResources.getString(R.string.cancel), null);
                builder.show();
                return true;
        }
        return false;
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ignored) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventQueueChanged ignored) {
        updateGUI();
    }


    public void updateGUI() {
        Activity fragmentActivity = getActivity();
        if (fragmentActivity != null)
            fragmentActivity.runOnUiThread(() -> {
                ComboPlugin plugin = ComboPlugin.getPlugin();

                // state
                stateView.setText(plugin.getStateSummary());
                PumpState ps = plugin.getPump().state;
                if (ps.insulinState == PumpState.EMPTY || ps.batteryState == PumpState.EMPTY
                        || ps.activeAlert != null && ps.activeAlert.errorCode != null) {
                    stateView.setTextColor(Color.RED);
                } else if (plugin.getPump().state.suspended
                        || ps.activeAlert != null && ps.activeAlert.warningCode != null) {
                    stateView.setTextColor(Color.YELLOW);
                } else {
                    stateView.setTextColor(Color.WHITE);
                }

                // activity
                String activity = plugin.getPump().activity;
                activityView.setText(activity != null ? activity : "");

                if (plugin.isInitialized()) {
                    buttonsLayout.setVisibility(View.VISIBLE);

                    // battery
                    batteryView.setTextSize(20);
                    if (ps.batteryState == PumpState.EMPTY) {
                        batteryView.setText("{fa-battery-empty}");
                        batteryView.setTextColor(Color.RED);
                    } else if (ps.batteryState == PumpState.LOW) {
                        batteryView.setText("{fa-battery-quarter}");
                        batteryView.setTextColor(Color.YELLOW);
                    } else {
                        batteryView.setText("{fa-battery-full}");
                        batteryView.setTextColor(Color.WHITE);
                    }

                    // reservoir
                    if (ps.insulinState == PumpState.LOW) {
                        reservoirView.setTextColor(Color.YELLOW);
                        reservoirView.setText(R.string.combo_reservoir_low);
                    } else if (ps.insulinState == PumpState.EMPTY) {
                        reservoirView.setTextColor(Color.RED);
                        reservoirView.setText(R.string.combo_reservoir_empty);
                    } else {
                        reservoirView.setTextColor(Color.WHITE);
                        reservoirView.setText(R.string.combo_reservoir_normal);
                    }

                    // last connection
                    String minAgo = DateUtil.minAgo(plugin.getPump().lastSuccessfulCmdTime);
                    long min = (System.currentTimeMillis() - plugin.getPump().lastSuccessfulCmdTime) / 1000 / 60;
                    if (plugin.getPump().lastSuccessfulCmdTime + 60 * 1000 > System.currentTimeMillis()) {
                        lastConnectionView.setText(R.string.combo_pump_connected_now);
                        lastConnectionView.setTextColor(Color.WHITE);
                    } else if (plugin.getPump().lastSuccessfulCmdTime + 30 * 60 * 1000 < System.currentTimeMillis()) {
                        lastConnectionView.setText(getString(R.string.combo_no_pump_connection, min));
                        lastConnectionView.setTextColor(Color.RED);
                    } else {
                        lastConnectionView.setText(minAgo);
                        lastConnectionView.setTextColor(Color.WHITE);
                    }

/* reading the data that would be displayed here triggers pump bug
                    // last bolus
                    Bolus bolus = plugin.getPump().lastBolus;
                    if (bolus != null && bolus.timestamp + 6 * 60 * 60 * 1000 >= System.currentTimeMillis()) {
                        long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                        double bolusMinAgo = agoMsc / 60d / 1000d;
                        double bolusHoursAgo = agoMsc / 60d / 60d / 1000d;
                        // TODO i18n
                        if ((agoMsc < 60 * 1000)) {
                            lastBolusView.setText(String.format("%.1f U (now)", bolus.amount));
                        } else if (bolusMinAgo < 60) {
                            lastBolusView.setText(String.format("%.1f U (%d min ago)", bolus.amount, (int) bolusMinAgo));
//                            lastBolusView.setText(getString(R.string.combo_last_bolus, bolus.amount,
//                                    getString(R.string.minago, bolusMinAgo), DateUtil.timeString(bolus.timestamp)));
                        } else {
                            lastBolusView.setText(String.format("%.1f U (%.1f h ago)", bolus.amount, bolusHoursAgo));
//                            lastBolusView.setText(getString(R.string.combo_last_bolus, bolus.amount,
//                                    String.format("%.1f", bolusHoursAgo) + getString(R.string.hoursago), DateUtil.timeString(bolus.timestamp)));
                        }
                    } else {
                        lastBolusView.setText("");
                    }
*/

                    // TBR
                    String tbrStr = "";
                    if (ps.tbrPercent != -1 && ps.tbrPercent != 100) {
                        long minSinceRead = (System.currentTimeMillis() - plugin.getPump().state.timestamp) / 1000 / 60;
                        long remaining = ps.tbrRemainingDuration - minSinceRead;
                        if (remaining >= 0) {
                            tbrStr = getString(R.string.combo_tbr_remaining, ps.tbrPercent, remaining);
                        }
                    }
                    tempBasalText.setText(tbrStr);

                    // TODO clean up & i18n or remove
                    // Queued activities
                    Spanned status = ConfigBuilderPlugin.getCommandQueue().spannedStatus();
                    if (status.toString().equals("")) {
                        queueView.setVisibility(View.GONE);
                    } else {
                        queueView.setVisibility(View.VISIBLE);
                        queueView.setText("Queued activities:\n" + status);
                    }
                }
            });
    }
}
