/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package joliex.net;

import java.io.IOException;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.AndJarDeps;
import jolie.net.CommMessage;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;

@AndJarDeps({"bluetooth.jar"})
public class BluetoothService extends JavaService
{
	private class DiscoveryListenerImpl implements DiscoveryListener
	{
		final private Value value = Value.create();
		private boolean completed = false;

		public void deviceDiscovered( RemoteDevice btDevice, DeviceClass cod )
		{
			Value dValue = Value.create();
			dValue.getFirstChild( "address" ).setValue( btDevice.getBluetoothAddress() );
			try {
				dValue.getFirstChild( "name" ).setValue( btDevice.getFriendlyName( true ) );
			} catch( IOException e ) {}
			value.getChildren( "device" ).add( dValue );
		}
		
		public Value getResult()
		{
			synchronized( value ) {
				while( !completed ) {
					try {
						value.wait();
					} catch( InterruptedException e ) {}
				}
			}
			return value;
		}

		public void inquiryCompleted( int discType )
		{
			synchronized( value ) {
				completed = true;
				value.notify();
			}
		}

		public void servicesDiscovered( int transID, ServiceRecord[] serviceRecords )
		{
			ValueVector vec = value.getChildren( "service" );
			Value v;
			for( ServiceRecord record : serviceRecords) {
				v = Value.create();
				v.getFirstChild( "location" ).setValue(
					record.getConnectionURL( ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false )
				);
				vec.add( v );
			}
		}

		public void serviceSearchCompleted( int transID, int respCode ) {}
	}

	public CommMessage inquire( CommMessage message )
		throws FaultException
	{
		Value retValue = null;
		try {
			DiscoveryListenerImpl listener = new DiscoveryListenerImpl();
			LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(
					DiscoveryAgent.GIAC,
					listener
				);
			retValue = listener.getResult();
		} catch( BluetoothStateException e ) {
			throw new FaultException( e );
		}
		return CommMessage.createResponse( message, retValue );
	}

	public CommMessage setDiscoverable( CommMessage message )
		throws FaultException
	{
		boolean b = false;
		try {
			b = LocalDevice.getLocalDevice().setDiscoverable( message.value().intValue() );
		} catch( BluetoothStateException e ) {
			throw new FaultException ( e );
		}
		return CommMessage.createResponse( message, Value.create( b ) );
	}

/*	public CommMessage discoveryServices( CommMessage request )
		throws FaultException
	{
		DiscoveryListenerImpl listener = new DiscoveryListenerImpl();
		/*RemoteDevice dev = new RemoteDevice( message.value().strValue() );
		UUID[] uuids = new UUID[ 1 ];
		uuids[ 0 ] = new UUID( "1101", true );
		int attrSet[] = new int[1];
		attrSet[0] = 0x0100; // service name (primary language)
		try {
			LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(
							attrSet, uuids, dev, listener
						);
		} catch( BluetoothStateException e ) {
			throw new FaultException( e );
		}--/
		return CommMessage.createResponse( request, listener.getResult() );
	}*/
}
