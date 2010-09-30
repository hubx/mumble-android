package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.service.MumbleService;
import org.pcgod.mumbleclient.service.MumbleServiceConnection;
import org.pcgod.mumbleclient.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * The main server list activity.
 *
 * Shows a list of servers and allows connecting to these. Also provides
 * ways to start creating and editing servers.
 *
 * @author pcgod
 *
 */
public class ServerList extends ConnectedListActivity {

	private class ServerAdapter extends BaseAdapter {
		private final Context context;
		private final Cursor cursor;

		public ServerAdapter(final Context context_, final DbAdapter dbAdapter_) {
			context = context_;
			cursor = dbAdapter_.fetchAllServers();
			startManagingCursor(cursor);
		}

		@Override
		public final int getCount() {
			return cursor.getCount();
		}

		@Override
		public final Object getItem(final int position) {
			return getItemId(position);
		}

		@Override
		public final long getItemId(final int position) {
			cursor.moveToPosition(position);
			return cursor.getLong(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_ID));
		}

		@Override
		public final View getView(final int position, final View v,
				final ViewGroup parent) {
			final LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			final View row = inflater.inflate(
					android.R.layout.simple_list_item_2, null);

			final TextView nameText = (TextView) row
					.findViewById(android.R.id.text1);
			final TextView userText = (TextView) row
					.findViewById(android.R.id.text2);

			cursor.moveToPosition(position);

			final String serverName = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_NAME));
			final String serverHost = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_HOST));
			final int serverPort = cursor.getInt(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PORT));
			final String serverUsername = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_USERNAME));
			final String serverPassword = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PASSWORD));

			if ("".equals(serverName)) {
				nameText.setText(serverHost + ":" + serverPort);
				userText.setText(serverUsername);
			} else {
				nameText.setText(serverName);
				userText.setText(serverUsername + "@" + serverHost + ":"
						+ serverPort);
			}

			return row;
		}
	}

	long serverToDeleteId = -1;
	DbAdapter dbAdapter;

	private static final int ACTIVITY_ADD_SERVER = 0;
	private static final int ACTIVITY_CHANNEL_LIST = 1;
	private static final int DIALOG_DELETE_SERVER = 0;

	private static final int MENU_ADD_SERVER = Menu.FIRST;
	private static final int MENU_EDIT_SERVER = Menu.FIRST + 1;
	private static final int MENU_DELETE_SERVER = Menu.FIRST + 2;
	private static final int MENU_EXIT = Menu.FIRST + 3;
	private static final int MENU_CONNECT_SERVER = Menu.FIRST + 4;

	private Thread clientThread;

	@Override
	public final boolean onContextItemSelected(final MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_CONNECT_SERVER:
			onListItemClick(getListView(), getCurrentFocus(), info.position, getListAdapter().getItemId(info.position));
			return true;
		case MENU_EDIT_SERVER:
			editServer(getListAdapter().getItemId(info.position));
			return true;
		case MENU_DELETE_SERVER:
			serverToDeleteId = info.id;
			showDialog(DIALOG_DELETE_SERVER);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public final void onCreateContextMenu(final ContextMenu menu, final View v,
			final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		final int menuPosition = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
		final int serverId = (int) getListView().getItemIdAtPosition(menuPosition);
		final Cursor c = dbAdapter.fetchServer(serverId);
		final String name = getServerName(c);
		c.close();
		menu.setHeaderTitle(name);

		menu.add(0, MENU_CONNECT_SERVER, 1, "Connect").setIcon(
				android.R.drawable.ic_menu_view);
		menu.add(0, MENU_EDIT_SERVER, 1, "Edit").setIcon(
				android.R.drawable.ic_menu_edit);
		menu.add(0, MENU_DELETE_SERVER, 1, "Delete").setIcon(
				android.R.drawable.ic_menu_delete);
	}

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_ADD_SERVER, 0, "Add Server").setIcon(
				android.R.drawable.ic_menu_add);
		menu.add(0, MENU_EXIT, 0, "Exit").setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	@Override
	public final boolean onMenuItemSelected(final int featureId,
			final MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ADD_SERVER:
			addServer();
			return true;
		case MENU_EXIT:
			if (clientThread != null) {
				clientThread.interrupt();
			}
			finish();
			System.exit(0);
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	private void addServer() {
		final Intent i = new Intent(this, ServerInfo.class);
		startActivityForResult(i, ACTIVITY_ADD_SERVER);
	}

	private void editServer(long id) {
		final Intent i = new Intent(this, ServerInfo.class);
		i.putExtra("serverId", id);
		startActivityForResult(i, ACTIVITY_ADD_SERVER);
	}

	private Dialog createDeleteServerDialog() {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete this server?")
				.setCancelable(false).setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(final DialogInterface dialog,
									final int id) {
								if (serverToDeleteId > 0) {
									dbAdapter.deleteServer(serverToDeleteId);
									serverToDeleteId = -1;
									fillList();
									Toast.makeText(ServerList.this, R.string.server_deleted, Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							public void onClick(final DialogInterface dialog,
									final int id) {
								dialog.cancel();
							}
						});

		return builder.create();
	}

	private String getServerName(final Cursor c) {
		String name = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_NAME));
		if ("".equals(name)) {
			final String host = c.getString(c
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_HOST));
			final int port = c.getInt(c
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PORT));
			name = host + ":" + port;
		}
		return name;
	}

	@Override
	protected final void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		fillList();
	}

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		registerForContextMenu(getListView());
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		dbAdapter = new DbAdapter(this);
		dbAdapter.open();

		fillList();
	}

	@Override
	protected final Dialog onCreateDialog(final int id) {
		Dialog d;
		switch (id) {
		case DIALOG_DELETE_SERVER:
			d = createDeleteServerDialog();
			break;
		default:
			d = null;
		}
		return d;
	}

	@Override
	protected final void onDestroy() {
		super.onDestroy();

		dbAdapter.close();
	}

	@Override
	protected final void onListItemClick(final ListView l, final View v,
			final int position, final long id) {
		super.onListItemClick(l, v, position, id);

		connectServer(id);
	}

	/**
	 * Starts connecting to a server.
	 *
	 * @param id
	 */
	protected final void connectServer(final long id) {
		final Cursor c = dbAdapter.fetchServer(id);
		final String host = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_HOST));
		final int port = c.getInt(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PORT));
		final String username = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_USERNAME));
		final String password = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PASSWORD));
		c.close();

		mService.setServer(host, port, username, password);

		while (!mService.isConnected()) {
			Thread.yield();
		}

		if (clientThread != null) {
			clientThread.interrupt();
		}

		final Intent i = new Intent(this, ChannelList.class);
		startActivityForResult(i, ACTIVITY_CHANNEL_LIST);
	}

	void fillList() {
		setListAdapter(new ServerAdapter(this, dbAdapter));
	}
}
