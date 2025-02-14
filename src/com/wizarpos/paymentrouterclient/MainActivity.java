package com.wizarpos.paymentrouterclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

import com.wizarpos.payment.aidl.IPaymentPay;
import com.wizarpos.payment.aidl.IPaymentPayCallback;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements OnClickListener {

	private static final String TAG = "PaymentRouterClient";
	private String param, response,callback, oldInvoice;

	private IPaymentPay mWizarPayment;
	final ServiceConnection mConnPayment = new PaymentConnection();

	public static Context _Context;

	public static String ExchangeKey = "ExchangeKey";
	public static String Settle = "Settle";
	public static String PrintLast = "PrintLast";

	public static String Purchase = "Purchase";
	public static String Reversal = "Reversal";
	public static String Refund = "Refund";

	public static String PreAuth = "PreAuth";
	public static String AuthCompletion = "AuthCompletion";
	public static String AuthCancel = "AuthCancel";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		_Context = getApplicationContext();

		int[] btnIds = { R.id.bind, R.id.unbind,R.id.setListener,
			R.id.Login, R.id.settle, R.id.Printlast,
			R.id.Sale, R.id.VoidSale, R.id.Refund,
//			R.id.PreAuth, R.id.AuthCancel, R.id.AuthComp,
			R.id.CancelRequest
		};

		for (int id : btnIds) {
			findViewById(id).setOnClickListener(this);
		}


	}

	@Override
	public void onBackPressed() {
		System.exit(0);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindPaymentRouter();
	}

	class PaymentConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName compName, IBinder binder) {
			Log.d(TAG, "onServiceConnected compName: " + compName);
			mWizarPayment = IPaymentPay.Stub.asInterface(binder);
			showResponse("Connect Success!");
		}

		@Override
		public void onServiceDisconnected(ComponentName compName) {
			Log.d(TAG, "onServiceDisconnected compName: " + compName);
			mWizarPayment = null;
			showResponse("Disconnect Success!");
		}
	};

	private void bindPaymentRouter() {
		if (mWizarPayment == null) {
			Log.i(TAG,"bindPaymentRouter");
			Intent intent = new Intent("com.wizarpos.payment.aidl.pay");
			intent.setPackage("com.wizarpos.ecobank");//The package name of the payment app
			bindService(intent, mConnPayment, BIND_AUTO_CREATE);
		}
	}
	private void unbindPaymentRouter() {
		if (mWizarPayment != null) {
			unbindService(mConnPayment);
			mWizarPayment = null;
		}
	}



	public void showResponse(String response) {
		this.response = response;
		showResponse();

	}

	public void showCallBack(String str){
		this.callback = str;
		showResponse();
	}

	public void showResponse() {
		setTextById(R.id.param, param);
		setTextById(R.id.result, response);
		setTextById(R.id.callback, callback);
	}
	private void setTextById(int id, CharSequence text) {
		runOnUiThread(new Runnable() {
			public void run() {
				((TextView)findViewById(id)).setText(text);
			}
		});


	}



	@Override
	public void onClick(final View view) {
		final int btnId = view.getId();
		setTextById(R.id.method, ((TextView)view).getText());

		param = "";
		response = "";
		callback = "";
		switch(btnId) {
		case R.id.bind:				bindPaymentRouter();    break;
		case R.id.unbind:			unbindPaymentRouter();  break;
		case R.id.VoidSale:
		case R.id.Refund:
			showInputDialog("Please input old Invoice Num",6,btnId);		break;
		default:
			if (mWizarPayment == null) {
				response = "Please click [ConnectPaymentRouter First]!";
			} else if(!mWizarPayment.asBinder().isBinderAlive()){
				response = "Please click [ConnectPaymentRouter First]!";
			}else if (null == (param = getParam(btnId))) {
				response = "Call parameter failed!";
			}

			if(btnId == R.id.CancelRequest){
				new Thread(new Runnable() {
					@Override
					public void run() {
                        boolean bRet = false;
                        try {
                            bRet = mWizarPayment.cancelRequest("");
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
						String cancelResult = bRet? "Cancel success" : "Cancel failed";
						Log.i(TAG,"cancelRequest ---->" + cancelResult);
                        showResponse(cancelResult);
					}
				}).start();



			}
			else if (response == "") {
				createAsyncTask().execute(btnId);
				return;
			}
			break;
		}
		showResponse();
	}


	private String getParam(int btnId) {
		JSONObject jsonObject = new JSONObject();
		try {
			switch(btnId) {
			case R.id.Login:			setParam4ExchangeKey(jsonObject);	break;
			case R.id.settle:			setParam4Settle(jsonObject);		break;
			case R.id.Printlast:		setParam4PrintLast(jsonObject);		break;
			case R.id.Sale:				setParam4Sale(jsonObject);			break;
			case R.id.VoidSale:			setParam4VoidSale(jsonObject);		break;
			case R.id.Refund:			setParam4Refund(jsonObject);	break;
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
		return jsonObject.toString();
	}

	public IPaymentPayCallback paymentPayCallback = new IPaymentPayCallback.Stub() {
		@Override
		public void process(int processCode, String processMsg) throws RemoteException {
			String str = "Callback->Code:" + processCode + ",processMsg:" + processMsg;
			Log.w(TAG, str);

			showCallBack(str);

		}

	};

	@SuppressLint("StaticFieldLeak")
	private AsyncTask<Integer, Void, String> createAsyncTask() {
		return new AsyncTask<Integer, Void, String>() {
			protected void onPreExecute() {
				showResponse("...");
			}
			protected String doInBackground(Integer...btnIds) {
				Log.d(TAG, "Request: " + param + " mWizarPayment: " + mWizarPayment);

				String result = "Skipped";
				try {
					switch(btnIds[0]) {
					case R.id.Login:
					case R.id.settle:
					case R.id.Printlast:
					case R.id.Sale:
					case R.id.VoidSale:
					case R.id.Refund:
						result = mWizarPayment.transact(param);
						break;
					case R.id.setListener:
						mWizarPayment.addProcedureCallback(paymentPayCallback);
						break;
					}
				} catch (RemoteException e) {
					result = e.getMessage();
				}

				Log.d(TAG, "Response: " + result);

				return result;
			}
			protected void onPostExecute(String result) {
				showResponse(result);
			}
		};
	}

	/**
	 * {"CurrencyCode":"156","ReqTransDate":"20250108","EnableReceipt":true,"ReqTransTime":"095400",
	 * "TransAmount":"2160","TransIndexCode":"861953384905834496","TransType":"Purchase"}
	 * */

	private void setParam4ExchangeKey(JSONObject jsonObject) throws JSONException {

		jsonObject.put("TransType", ExchangeKey);
		jsonObject.put("ShowUI", false);
	}

	private void setParam4Settle(JSONObject jsonObject) throws JSONException {

		jsonObject.put("TransType", Settle);
		jsonObject.put("ShowUI", false);
	}

	private void setParam4PrintLast(JSONObject jsonObject) throws JSONException {


		jsonObject.put("TransType", PrintLast);
		jsonObject.put("ShowUI", false);
	}

	private void setParam4Sale(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", Purchase);
		jsonObject.put("TransIndexCode", "00000001");
		jsonObject.put("CurrencyCode","936");
		jsonObject.put("TransAmount","1234");
		jsonObject.put("EnableReceipt",false);

		jsonObject.put("ReqTransDate","20250214");
		jsonObject.put("ReqTransTime","095400");

		jsonObject.put("ShowUI", false);
	}

	private void setParam4VoidSale(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", Reversal);
		jsonObject.put("TransIndexCode", "00000002");
		jsonObject.put("CurrencyCode","936");
		jsonObject.put("TransAmount","1234");
		jsonObject.put("EnableReceipt",false);
		jsonObject.put("OriInvoiceNum", oldInvoice);


		jsonObject.put("ReqTransDate","20250214");
		jsonObject.put("ReqTransTime","095400");

		jsonObject.put("ShowUI", false);
	}

	private void setParam4Refund(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", Refund);
		jsonObject.put("TransIndexCode", "00000003");
		jsonObject.put("CurrencyCode","936");
		jsonObject.put("TransAmount","1234");
		jsonObject.put("EnableReceipt",false);
		jsonObject.put("OriInvoiceNum", oldInvoice);


		jsonObject.put("ReqTransDate","20250214");
		jsonObject.put("ReqTransTime","095400");

		jsonObject.put("ShowUI", false);
	}



	private void showInputDialog(String title,int maxInput,final int btnID) {
		oldInvoice = "";

		final EditText editText = new EditText(MainActivity.this);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		InputFilter[] filters = {new InputFilter.LengthFilter(maxInput)};
		editText.setFilters(filters);
		AlertDialog.Builder inputDialog =
			new AlertDialog.Builder(MainActivity.this);
		inputDialog.setTitle(title).setView(editText);
		inputDialog.setNegativeButton("Cancel",null);
		inputDialog.setPositiveButton("Confirm",
			(dialog, which) -> {
				switch (btnID){
				case R.id.VoidSale:
				case R.id.Refund:
					oldInvoice = editText.getText().toString();
					break;
				}

				if (mWizarPayment == null) {
					response = "Please click [ConnectPaymentRouter First]!";
				} else if (null == (param = getParam(btnID))) {
					response = "Call parameter failed!";
				}
				if (response == "") {
					createAsyncTask().execute(btnID);
					return;
				}

				showResponse();
			}).show();
	}


}
