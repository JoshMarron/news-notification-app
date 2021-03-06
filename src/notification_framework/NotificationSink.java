package notification_framework;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

/*
This is the sink part of the notification framework, capable of receiving notifications from sources
It extends UnicastRemoteObject and implements a remote interface to allow for remote access to some specified methods
 */
public class NotificationSink extends UnicastRemoteObject implements NotificationSinkInterface {

    private String id;
    private HashSet<Topic> topics;
    private LinkedBlockingQueue<NotifiableEvent> notificationQueue; //Contains received notifications

    public NotificationSink() throws RemoteException {
        super(); //Need to call the constructor of UnicastRemoteObject
        this.id = NotificationFramework.generateSinkID(); //Generate a unique ID
        this.notificationQueue = new LinkedBlockingQueue<>();
        this.topics = new HashSet<>();
    }

    //Make 3 attempts to connect to the source, if failure then throw exception
    public void registerToSource(Topic topic) throws ConnectException {
        int attempts = 1;
        while(true) {
            try {
                //Get the source associated to the topic from the registry
                NotificationSourceInterface source = (NotificationSourceInterface) Naming.lookup(NotificationFramework.hostname + topic.getCode());
                source.register(this);
                topics.add(topic);
                return;
            } catch (NotBoundException | RemoteException e) {
                if (attempts < 4) {
                    attempts++;
                }
                else {
                    throw new ConnectException("Could not connect to registry or source");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Remotely accessible method which allows a source to notify the sink of an event, passing in a notification
    //This puts the received notification into the queue where it can be accessed by an application
    //Returns true if successful
    @Override
    public boolean notifyOfEvent(Notification<? extends NotifiableEvent> notification) throws RemoteException {
        try {
            notificationQueue.put(notification.getEvent());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    //Allows for remote access of this sink's ID
    @Override
    public String getID() throws RemoteException {
        return id;
    }

    /*
    Used by the client to get the next notification
    Blocks until a notification is received
     */
    public NotifiableEvent takeNotification() {
        try {
            NotifiableEvent event = notificationQueue.take();
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    //Unregisters this sink from a topic
    //Make three attempts then throw an exception if not successful.
    public void unsubscribe(Topic topic) throws ConnectException{
        int attempts = 1;
        while(true) {
            try {
                NotificationSourceInterface source = (NotificationSourceInterface) Naming.lookup(NotificationFramework.hostname + topic.getCode());
                source.unregister(this.id);
                return;
            } catch (NotBoundException e) {
                throw new ConnectException("This topic does not exist or has been taken down.");
            } catch (MalformedURLException e) {
                throw new ConnectException("The provided topic name contains illegal characters.");
            } catch (RemoteException e) {
                if (attempts < 4) {
                    attempts++;
                }
                else {
                    throw new ConnectException("This source has already been taken down.");
                }
            }
        }
    }

    //Allows a sink to unsubscribe from all its sources
    public void unsubscribeAll() throws ConnectException {
        for (Topic topic: this.topics) {
            this.unsubscribe(topic);
            this.topics.remove(topic);
        }
    }

}
