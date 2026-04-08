print("waiting for a modem_message or key event")
while True:
    event = os.pull_event()
    print("event:", event)
    if event and event[0] == "key":
        break
