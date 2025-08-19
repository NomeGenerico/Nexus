# Nexus Server

import socket
import json
import threading
import subprocess
import os
import time
from zeroconf import Zeroconf, ServiceInfo
# import pyautogui


HOST = '0.0.0.0'
NAME = "Kryptos"
env = os.environ.copy()
env["DISPLAY"] = ":0"

def handle_client(conn, addr):
	print(f"[+] Connected by {addr}")
	try:



		
		data = conn.recv(1024).decode()
		if not data:
			print(f"[-] Connection closed by {addr}")

		try:
			# message1 = data.decode().strip()

			message = json.loads(data)

			if not message:
				print("[!] Warning: Empty message received")
			else:
				print(f"[>] Received message: {message}")

		except json.JSONDecodeError as e:
			print(f"[!] JSON decode error: {e}")

		match message.get("type"):
			case "cast_url":
				url  = message.get("url")
				if url:
					comand = [
						'firefox',
						"--kisosk",
						"--new-window",
						"--no-remote",
						"--new-instance",
						url
					]
					
					print(f"[~] Opening URL: {url}")
					subprocess.run(["pkill", "-f", "firefox"])
					time.sleep(1)

					# subprocess.Popen(['xdg-open',url])
					subprocess.run(comand, env=env)
					print("Running", " ".join(comand))
			case "move":
				dx = message.get("dx", 0)
				dy = message.get("dy", 0)
				"pyautogui.moveRel(dx, dy)"
			case "click":
				button = message.get("button", "left")
				"pyautogui.click(button=button)"
			case "scroll":
				dy = message.get("dy", 0)
				"pyautogui.scroll(-int(dy))"  # Negative to match natural scrolling	

	except Exception as e:
		print("[!] Error:", e)

	finally:
		conn.close()

def start_server():
	
	zeroconf = None
	info = None
	PORT = 6565

	sucesseful = False
	while (not(sucesseful)):
		try:
			desc = {'version': '0.1'}
			ip_addr = socket.gethostbyname(socket.gethostname())
			zeroconf = Zeroconf()
			info = ServiceInfo(
				"_nexus._tcp.local.",
				f"{NAME}._nexus._tcp.local.",
				addresses=[socket.inet_aton(ip_addr)],
				port=PORT,
				properties=desc,
				server="nexus.local.",
			)

			zeroconf.unregister_service(info)
			zeroconf.register_service(info)
			print(f"[+] Registered mDNS as nexus.local ({ip_addr}:{PORT})")

			with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
				s.bind((HOST, PORT))
				s.listen()
				print(f"[*] Listening on {HOST}:{PORT}")
				while True:
					conn, addr = s.accept()
					threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()

		except KeyboardInterrupt:
			print("\n[!] Server is shutting down due to keyboard interrupt...")
			return

		except Exception as e:
			print(f"\n[!] Server error: {e}")
			# PORT = PORT + 1

		finally:
			if zeroconf and info:
				zeroconf.unregister_service(info)
				zeroconf.close()
				print(f"[+] Zeroconf service unregistered and closed")

if __name__ == "__main__":
	start_server()
