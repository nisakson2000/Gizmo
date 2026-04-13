#!/bin/bash
cd "$HOME/gizmo"
echo "Stopping Gizmo..."
podman compose down
echo "All services stopped."
