package com.wizarpos.paymentrouterclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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

import com.wizarpos.aidl.ICloudPay;
import com.wizarpos.location.service.ILocationApiMgr;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements OnClickListener {

	private static final String TAG = "PaymentRouterClient";
	private String param, response, oriInvoice;

	private ICloudPay mWizarPayment;
	private ILocationApiMgr iLocationApiMgr;
	final ServiceConnection mConnPayment = new PaymentConnection();

	public static Context _Context;

	public static String TEST_IP1 = "113.164.14.80";
	public static int TEST_PORT1 = 11251;
	public static String TEST_IP2 = "113.164.14.80";
	public static int TEST_PORT2 = 11251;

	private String LOCATION_PACKAGE = "com.wizarpos.location.service";
	private String LOCATION_ACTION = "com.wizarpos.location.service.MainService";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		_Context = getApplicationContext();

		int[] btnIds = { R.id.bind, R.id.unbind,R.id.setParams,
			R.id.login, R.id.settle, R.id.printlast,
			R.id.payCash, R.id.voidSale, R.id.refund,
			R.id.getLocation
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
			mWizarPayment = ICloudPay.Stub.asInterface(binder);
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
			Intent intent = new Intent("com.wizarpos.aidl.ICloudPay");
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


	//Location Aidl
	private void bindLocationServer() {
		if(iLocationApiMgr==null) {
			Intent intent = new Intent();
			ComponentName componentName = new ComponentName(LOCATION_PACKAGE, LOCATION_ACTION);
			intent.setComponent(componentName);
			bindService(intent, mLocationServer, BIND_AUTO_CREATE);
		}
	}
	private void unBindLocationServer(){
		if(iLocationApiMgr!=null){
			unbindService(mLocationServer);
			iLocationApiMgr = null;
		}
	}

	ServiceConnection mLocationServer = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName compName, IBinder service) {
			Log.d(TAG, "onServiceConnected compName: " + compName);
			iLocationApiMgr = ILocationApiMgr.Stub.asInterface(service);
			try {
				String sLocation = iLocationApiMgr.getLocation();
				Log.d(TAG, "getLocation: " + sLocation);
				showResponse(sLocation);
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName compName) {
			Log.d(TAG, "onServiceDisconnected compName: " + compName);
			iLocationApiMgr = null;
		}
	};

	public void showResponse(String response) {
		this.response = response;
		showResponse();

	}

	public void showResponse() {
		setTextById(R.id.param, param);
		setTextById(R.id.result, response);
	}
	private void setTextById(int id, CharSequence text) {
		((TextView)findViewById(id)).setText(text);
	}



	@Override
	public void onClick(final View view) {
		final int btnId = view.getId();
		setTextById(R.id.method, ((TextView)view).getText());

		param = "";
		response = "";

		switch(btnId) {
		case R.id.bind:				bindPaymentRouter();    break;
		case R.id.unbind:			unbindPaymentRouter();  break;
		case R.id.getLocation:
			unBindLocationServer();
			bindLocationServer();
			break;
		case R.id.voidSale:			showInputDialog("Please input old Invoice Num",6,btnId);		break;
		default:
			if (mWizarPayment == null) {
				response = "Please click [ConnectPaymentRouter First]!";
			} else if(!mWizarPayment.asBinder().isBinderAlive()){
				response = "Please click [ConnectPaymentRouter First]!";
			}else if (null == (param = getParam(btnId))) {
				response = "Call parameter failed!";
			}
			if (response == "") {
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
			case R.id.payCash:				setParam4PayCash(jsonObject);	break;
			case R.id.voidSale:				setParam4VoidSale(jsonObject);	break;
			case R.id.refund:				setParam4Refund(jsonObject);	break;
			case R.id.printlast:			setParam4getPrintLast		(jsonObject);	break;
			case R.id.settle:				setParam4settle				(jsonObject);	break;
			case R.id.setParams:			setParam4SetParams(jsonObject);	break;
			case R.id.login:				break;
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
		return jsonObject.toString();
	}

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
					case R.id.payCash:
					case R.id.voidSale:
					case R.id.refund:
						result = mWizarPayment.transact			(param);							break;
					case R.id.printlast:		result = mWizarPayment.printLast		(param);	break;
					case R.id.settle:			result = mWizarPayment.settle			(param);	break;
					case R.id.setParams:		result = mWizarPayment.setParams		(param);	break;
					case R.id.login:			result = mWizarPayment.login();						break;
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


	private void setParam4PayCash(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", 1);
		//'000000012300' means amount = 123.00
		jsonObject.put("TransAmount", "000000012300");
		jsonObject.put("currencyCode","935");
		// 0: enable receipt , 1: disbale receipt
//		jsonObject.put("NoPrintReceipt",1);
	}

	private void setParam4VoidSale(JSONObject jsonObject) throws JSONException {
		jsonObject.put("passWord", "123456");
		jsonObject.put("TransType", 101);
		jsonObject.put("currencyCode","936");
		if(oriInvoice !=null && oriInvoice.length()>0)
			jsonObject.put("oriInvoice", oriInvoice);

	}

	private void setParam4Refund(JSONObject jsonObject) throws JSONException {
		//'000000012300' means amount = 123.00
		jsonObject.put("TransAmount", "000000012300");
		jsonObject.put("passWord", "123456");
		jsonObject.put("TransType", 100);
		jsonObject.put("currencyCode","936");
	}

	private void setParam4getPrintLast(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", 147);
	}

	private void setParam4settle(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", 21);
	}


	private void setParam4Login(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", 55);
	}

	private void setParam4SetParams(JSONObject jsonObject) throws JSONException {
		jsonObject.put("TransType", 2001);
		jsonObject.put("PrimaryIP",TEST_IP1);
		jsonObject.put("SecondaryIP",TEST_IP2);
		jsonObject.put("PrimaryPort",TEST_PORT1);
		jsonObject.put("SecondaryPort",TEST_PORT2);

		jsonObject.put("TID","00000000"); //Max to 8
		jsonObject.put("MID","123456789012345"); //Max to 15

		jsonObject.put("passWord", "123456");

	}




	private void showInputDialog(String title,int maxInput,final int btnID) {
		oriInvoice = "";

		final EditText editText = new EditText(MainActivity.this);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		InputFilter[] filters = {new InputFilter.LengthFilter(maxInput)};
		editText.setFilters(filters);
		AlertDialog.Builder inputDialog =
			new AlertDialog.Builder(MainActivity.this);
		inputDialog.setTitle(title).setView(editText);
		inputDialog.setNegativeButton("Cancel",null);
		inputDialog.setPositiveButton("Confirm",
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (btnID){
					case R.id.voidSale:			oriInvoice = editText.getText().toString(); 		break;
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
				}
			}).show();
	}


}
