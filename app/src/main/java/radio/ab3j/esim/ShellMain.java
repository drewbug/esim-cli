package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.telephony.SubscriptionInfo;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;

import java.util.List;

public class ShellMain {

  public static void main(String[] args) {
    List<UiccCardInfo> cards = ServiceManager.getTelephonyManager().getUiccCardsInfo();

    for (UiccCardInfo card : cards) {
        System.out.println(card.toString());

        for (UiccPortInfo port : card.getPorts()) {
            System.out.println(port.toString());
        }

        System.out.println();
    }

    android.util.Log.d("esim", "it begins");

    List<SubscriptionInfo> subs = ServiceManager.getSubscriptionService().getAvailableSubscriptionInfoList();

    android.util.Log.d("esim", Boolean.toString(subs.isEmpty()));

    for (SubscriptionInfo sub : subs) {
        System.out.println(sub.toString());
    }
  }

}
