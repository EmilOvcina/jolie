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


package jolie.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import jolie.Constants;
import jolie.Interpreter;
import jolie.net.CommChannel;
import jolie.net.CommMessage;
import jolie.net.ListCommChannel;
import jolie.util.Pair;


abstract public class JavaService
{
	private Interpreter interpreter;
	final private Map< String, Pair< Constants.OperationType, Method > > operations =
					new HashMap< String, Pair< Constants.OperationType, Method > >();
	final private Object[] args = new Object[1];
	
	public JavaService()
	{
		Class<?>[] params, exceptions;
		Class<?> returnType;
		Method[] methods = this.getClass().getMethods();
		for( Method method : methods ) {
			params = method.getParameterTypes();
			if (
				params.length == 1 &&
				params[0].isAssignableFrom( CommMessage.class )
			) {
				returnType = method.getReturnType();
				if ( CommMessage.class.isAssignableFrom( returnType ) ) {
					// It's a Request-Response operation
					exceptions = method.getExceptionTypes();
					if ( exceptions.length == 0 ||
						( exceptions.length == 1 && FaultException.class.isAssignableFrom( exceptions[0] ) )
						)
					{
					
						operations.put(
							method.getName(),
							new Pair< Constants.OperationType, Method >( Constants.OperationType.REQUEST_RESPONSE, method )
						);
					}
				} else if ( void.class.isAssignableFrom( returnType ) ) {
					// It's a One-Way operation
					exceptions = method.getExceptionTypes();
					if ( exceptions.length == 0 ) {
						operations.put(
							method.getName(),
							new Pair< Constants.OperationType, Method >( Constants.OperationType.ONE_WAY, method )
						);
					}
				}
			}
		}
	}
	
	public CommMessage callOperation( CommMessage message )
		throws InvalidIdException, IllegalAccessException
	{
		Pair< Constants.OperationType, Method > pair = operations.get( message.operationName() );
		if ( pair == null ) {
			throw new InvalidIdException( message.operationName() );
		}
		CommMessage ret = null;
		args[0] = message;
		if ( pair.key() == Constants.OperationType.ONE_WAY ) {
			try {
				pair.value().invoke( this, args );
			} catch( InvocationTargetException e ) {
				// This should never happen, as we filtered this out in the constructor.
				e.printStackTrace();
			}
		} else {
			try {
				ret = (CommMessage)pair.value().invoke( this, args );
			} catch( InvocationTargetException e ) {
				if ( e.getCause() instanceof FaultException ) {
					ret = new CommMessage(
							message.operationName(),
							"/",
							(FaultException)e.getCause()
							);
				} else {
					// This should never happen, as we filtered this out in the constructor.
					e.printStackTrace();
				}
			}
		}
		return ret;
	}
	
	protected void setInterpreter( Interpreter interpreter )
	{
		this.interpreter = interpreter;
	}
	
	protected Interpreter interpreter()
	{
		return interpreter;
	}
	
	public CommChannel sendMessage( CommMessage message )
	{
		ListCommChannel c = new ListCommChannel(
							new Vector< CommMessage >(),
							new Vector< CommMessage >()
							);
		c.inputList().add( message );
		interpreter.commCore().scheduleReceive( c, null );
		return new ListCommChannel( c.outputList(), c.inputList() );
	}
}